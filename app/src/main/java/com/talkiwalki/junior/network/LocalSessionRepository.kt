package com.talkiwalki.junior.network

import com.talkiwalki.junior.core.coroutines.DefaultDispatcherProvider
import com.talkiwalki.junior.audio.UdpVoiceCodec
import com.talkiwalki.junior.audio.UdpVoicePacket
import com.talkiwalki.junior.audio.PttArbiter
import com.talkiwalki.junior.core.coroutines.DispatcherProvider
import com.talkiwalki.junior.domain.model.Participant
import com.talkiwalki.junior.domain.model.ParticipantRole
import com.talkiwalki.junior.network.protocol.LocalProtocol
import com.talkiwalki.junior.network.protocol.LocalProtocol.Message
import com.talkiwalki.junior.network.protocol.LocalProtocol.WireParticipant
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LocalSessionState(
    val groupName: String = "",
    val hostAddress: String? = null,
    val port: Int = LocalProtocol.DEFAULT_PORT,
    val participants: List<Participant> = emptyList(),
    val selfId: String? = null,
    val speakerId: String? = null,
    val isHost: Boolean = false,
    val error: String? = null,
    val isGroupCancelled: Boolean = false
)

/** Owns one local TCP session. It never performs service discovery or Internet I/O. */
class LocalSessionRepository(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    private val scope: CoroutineScope = CoroutineScope(DefaultDispatcherProvider.io)
) {
    private val stateFlow = MutableStateFlow(LocalSessionState())
    val state: StateFlow<LocalSessionState> = stateFlow.asStateFlow()
    private val incomingAudioFlow = MutableSharedFlow<UdpVoicePacket>(extraBufferCapacity = 64)
    val incomingAudio: SharedFlow<UdpVoicePacket> = incomingAudioFlow.asSharedFlow()
    private val lock = Mutex()
    private val peerSlots = Semaphore(MAX_CLIENTS)
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null
    private var heartbeatJob: Job? = null
    private var pttExpiryJob: Job? = null
    private var clientSocket: Socket? = null
    private var clientWriter: BufferedWriter? = null
    private var clientReaderJob: Job? = null
    private var sessionId = ""
    private var isHost = false
    private val peers = linkedMapOf<String, Peer>()
    private val pttArbiter = PttArbiter()
    private var hostId = ""
    private var udpSocket: DatagramSocket? = null
    private var udpJob: Job? = null
    private var udpSendJob: Job? = null
    private var outgoingAudio: Channel<OutgoingAudio>? = null
    private var udpHost: InetSocketAddress? = null
    private val udpPeers = linkedMapOf<String, InetSocketAddress>()
    private var audioSequence = 0

    suspend fun host(nickname: String, groupName: String): Result<Unit> = runCatching {
        close()
        withContext(dispatchers.io) {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(LocalProtocol.DEFAULT_PORT))
            server = socket
            isHost = true
            sessionId = UUID.randomUUID().toString()
            val host = Participant("host-$sessionId", nickname, ParticipantRole.HOST)
            hostId = host.id
            stateFlow.value = LocalSessionState(groupName, LocalNetworkInfo.localIpv4Address(), socket.localPort, listOf(host), selfId = host.id, isHost = true)
            startUdp(UDP_PORT, null)
            acceptJob = scope.launch { acceptLoop(socket) }
            startPttExpiryMonitor()
        }
    }.onFailure { fail("Impossible de démarrer le groupe : ${it.userMessage()}") }

    suspend fun join(nickname: String, endpoint: String): Result<Unit> {
        val result = runCatching {
        close()
        val (address, port) = parseEndpoint(endpoint)
        withContext(dispatchers.io) {
            val socket = Socket()
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            clientSocket = socket
            clientWriter = writer(socket)
            send(clientWriter!!, LocalProtocol.Join(nickname))
            val inputReader = reader(socket)
            val first = read(inputReader)
            when (first) {
                is LocalProtocol.JoinAccepted -> {
                    isHost = false
                    sessionId = first.sessionId
                    stateFlow.value = LocalSessionState(first.groupName, address, port, selfId = first.participantId)
                    startUdp(0, InetSocketAddress(address, UDP_PORT))
                    send(clientWriter!!, LocalProtocol.UdpRegister(udpSocket!!.localPort))
                    clientReaderJob = scope.launch { clientReadLoop(socket, inputReader) }
                    startHeartbeat()
                }
                is LocalProtocol.JoinRejected -> error(first.reason)
                is LocalProtocol.Error -> error(first.reason)
                else -> error("Réponse du serveur invalide.")
            }
        }
        }
        if (result.isFailure) {
            close()
            fail("Connexion impossible : ${result.exceptionOrNull()?.userMessage()}")
        }
        return result
    }

    suspend fun close() {
        lock.withLock {
            heartbeatJob?.cancel(); heartbeatJob = null
            pttExpiryJob?.cancel(); pttExpiryJob = null
            clientReaderJob?.cancel(); clientReaderJob = null
            runCatching { clientWriter?.let { send(it, LocalProtocol.Leave) } }
            runCatching { clientSocket?.close() }; clientSocket = null; clientWriter = null
            acceptJob?.cancel(); acceptJob = null
            runCatching { server?.close() }; server = null
            peers.values.forEach { it.close() }; peers.clear()
            udpJob?.cancel(); udpJob = null
            udpSendJob?.cancel(); udpSendJob = null
            outgoingAudio?.close(); outgoingAudio = null
            runCatching { udpSocket?.close() }; udpSocket = null; udpPeers.clear(); udpHost = null
            isHost = false; sessionId = ""; hostId = ""
            stateFlow.value = LocalSessionState()
        }
    }

    private suspend fun acceptLoop(serverSocket: ServerSocket) {
        while (scope.isActive && !serverSocket.isClosed) {
            try {
                val socket = serverSocket.accept()
                // Never create an unbounded number of peer coroutines. A hostile
                // device or a broken network must not exhaust the app heap.
                if (!peerSlots.tryAcquire()) {
                    socket.close()
                    continue
                }
                scope.launch {
                    try {
                        handlePeer(socket)
                    } finally {
                        peerSlots.release()
                    }
                }
                // A small yield also prevents a stream of immediately failing
                // incoming sockets from spinning the accept loop at full speed.
                delay(ACCEPT_THROTTLE_MS)
            } catch (_: SocketException) {
                break
            } catch (_: Exception) {
                if (!serverSocket.isClosed) delay(ACCEPT_FAILURE_BACKOFF_MS) else break
            }
        }
    }

    private suspend fun handlePeer(socket: Socket) {
        socket.soTimeout = READ_TIMEOUT_MS
        val reader = reader(socket); val writer = writer(socket)
        try {
            val hello = read(reader)
            if (hello !is LocalProtocol.Join) { send(writer, LocalProtocol.JoinRejected("JOIN attendu.")); return }
            val id = UUID.randomUUID().toString()
            val peer = Peer(id, hello.nickname, socket, writer)
            lock.withLock {
                if (!isHost) return
                peers[id] = peer
                send(writer, LocalProtocol.JoinAccepted(sessionId, stateFlow.value.groupName, id))
                broadcastParticipants()
            }
            while (scope.isActive) {
                when (val control = read(reader)) {
                    LocalProtocol.Leave -> break
                    LocalProtocol.Heartbeat -> peer.lastSeen = System.currentTimeMillis()
                    LocalProtocol.PttRequest -> handlePttRequest(peer.id)
                    LocalProtocol.PttRelease -> handlePttRelease(peer.id)
                    is LocalProtocol.UdpRegister -> udpPeers[peer.id] = InetSocketAddress(socket.inetAddress, control.port)
                    else -> send(writer, LocalProtocol.Error("Message non autorisé."))
                }
            }
        } catch (_: Exception) {
            // Socket closure and malformed remote input both result in removing this peer.
        } finally {
            lock.withLock {
                val disconnectedId = peers.entries.firstOrNull { it.value.socket == socket }?.key
                peers.entries.removeIf { it.value.socket == socket }
                if (disconnectedId != null && pttArbiter.disconnect(disconnectedId)) broadcastPttState()
                broadcastParticipants()
            }
            runCatching { socket.close() }
        }
    }

    private suspend fun clientReadLoop(socket: Socket, reader: BufferedReader) {
        try {
            while (scope.isActive) when (val message = read(reader)) {
                is LocalProtocol.ParticipantsUpdated -> stateFlow.value = stateFlow.value.copy(participants = message.participants.map { it.toParticipant() }, error = null)
                is LocalProtocol.Error -> fail(message.reason)
                is LocalProtocol.PttGranted -> stateFlow.value = stateFlow.value.copy(speakerId = message.speakerId)
                is LocalProtocol.PttDenied -> stateFlow.value = stateFlow.value.copy(speakerId = message.speakerId)
                is LocalProtocol.PttState -> stateFlow.value = stateFlow.value.copy(speakerId = message.speakerId)
                else -> Unit
            }
        } catch (_: Exception) {
            if (clientSocket == socket) cancelGroupBecauseHostDisconnected()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && clientWriter != null) {
                delay(HEARTBEAT_MS)
                val heartbeat = runCatching { clientWriter?.let { send(it, LocalProtocol.Heartbeat) } }
                if (heartbeat.isFailure) {
                    cancelGroupBecauseHostDisconnected()
                    break
                }
            }
        }
    }

    fun sendAudio(pcm: ByteArray) {
        val state = stateFlow.value; val id = state.selfId ?: return
        if (pcm.size > UdpVoiceCodec.MAX_PAYLOAD) return
        if (state.speakerId != id || pcm.size > UdpVoiceCodec.MAX_PAYLOAD) return
        // A single sender keeps 20 ms frames ordered and prevents coroutine bursts
        // from overloading slower Wi-Fi drivers.
        outgoingAudio?.trySend(OutgoingAudio(id, state.isHost, pcm))
    }

    private fun startUdp(port: Int, host: InetSocketAddress?) {
        udpJob?.cancel(); udpSendJob?.cancel(); outgoingAudio?.close(); udpSocket?.close(); udpHost = host
        val socket = DatagramSocket(port).apply {
            receiveBufferSize = UDP_BUFFER_BYTES
            sendBufferSize = UDP_BUFFER_BYTES
        }
        udpSocket = socket
        audioSequence = 0
        val sender = Channel<OutgoingAudio>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        outgoingAudio = sender
        udpSendJob = scope.launch {
            for (audio in sender) {
                val wire = UdpVoiceCodec.encode(UdpVoicePacket(audio.speakerId, audioSequence++, audio.pcm))
                runCatching {
                    if (audio.isHost) {
                        val leaseIsActive = lock.withLock { pttArbiter.renew(audio.speakerId) }
                        if (leaseIsActive) relayUdp(wire, audio.speakerId)
                    } else {
                        udpHost?.let { socket.send(DatagramPacket(wire, wire.size, it)) }
                    }
                }
            }
        }
        udpJob = scope.launch {
            val buffer = ByteArray(1_300)
            while (isActive && !socket.isClosed) {
                try {
                    val datagram = DatagramPacket(buffer, buffer.size); socket.receive(datagram)
                    val voice = UdpVoiceCodec.decode(datagram.data, datagram.length) ?: continue
                    if (isHost) {
                        val expected = udpPeers[voice.speakerId] ?: continue
                        if (expected.address != datagram.address || expected.port != datagram.port) continue
                        val leaseIsActive = lock.withLock {
                            pttArbiter.currentSpeaker() == voice.speakerId && pttArbiter.renew(voice.speakerId)
                        }
                        if (!leaseIsActive) continue
                        incomingAudioFlow.tryEmit(voice); relayUdp(datagram.data.copyOf(datagram.length), voice.speakerId)
                    } else if (datagram.address == host?.address && voice.speakerId != stateFlow.value.selfId) incomingAudioFlow.tryEmit(voice)
                } catch (_: Exception) { if (!socket.isClosed) delay(20) }
            }
        }
    }
    private fun relayUdp(bytes: ByteArray, sender: String) { val socket = udpSocket ?: return; udpPeers.filterKeys { it != sender }.values.forEach { target -> runCatching { socket.send(DatagramPacket(bytes, bytes.size, target)) } } }

    /** Releases a turn which has received no audio frames for ten seconds. */
    private fun startPttExpiryMonitor() {
        pttExpiryJob?.cancel()
        pttExpiryJob = scope.launch {
            while (isActive && isHost) {
                delay(PTT_EXPIRY_CHECK_MS)
                lock.withLock {
                    if (stateFlow.value.speakerId != pttArbiter.currentSpeaker()) broadcastPttState()
                }
            }
        }
    }

    /**
     * PTT writes to the TCP control socket. Keep the dispatcher boundary here so
     * this method remains safe if a UI caller accidentally invokes it on Main.
     */
    suspend fun requestPtt(): Boolean = withContext(dispatchers.io) {
        lock.withLock {
            if (isHost) {
                if (pttArbiter.request(hostId)) broadcastPttState()
                stateFlow.value.speakerId == hostId
            } else {
                clientWriter?.let { send(it, LocalProtocol.PttRequest) }
                false
            }
        }
    }

    suspend fun releasePtt() = withContext(dispatchers.io) {
        lock.withLock {
            if (isHost) {
                if (pttArbiter.release(hostId)) broadcastPttState()
            } else {
                clientWriter?.let { send(it, LocalProtocol.PttRelease) }
            }
        }
    }

    private suspend fun handlePttRequest(id: String) {
        val granted = pttArbiter.request(id)
        peers[id]?.let { send(it.writer, if (granted) LocalProtocol.PttGranted(id) else LocalProtocol.PttDenied(pttArbiter.currentSpeaker())) }
        if (granted) broadcastPttState()
    }
    private suspend fun handlePttRelease(id: String) { if (pttArbiter.release(id)) broadcastPttState() }
    private suspend fun broadcastPttState() {
        val speaker = pttArbiter.currentSpeaker()
        stateFlow.value = stateFlow.value.copy(speakerId = speaker)
        val message = LocalProtocol.PttState(speaker)
        peers.values.forEach { runCatching { send(it.writer, message) } }
    }

    private suspend fun broadcastParticipants() {
        val host = stateFlow.value.participants.firstOrNull { it.role == ParticipantRole.HOST }
        val participants = listOfNotNull(host) + peers.values.map { Participant(it.id, it.name, ParticipantRole.PARTICIPANT) }
        stateFlow.value = stateFlow.value.copy(participants = participants)
        val message = LocalProtocol.ParticipantsUpdated(participants.map { WireParticipant(it.id, it.displayName, it.role == ParticipantRole.HOST) })
        peers.values.toList().forEach { peer -> runCatching { send(peer.writer, message) }.onFailure { peers.remove(peer.id); peer.close() } }
    }

    private suspend fun cancelGroupBecauseHostDisconnected() {
        close()
        stateFlow.value = LocalSessionState(error = HOST_DISCONNECTED_MESSAGE, isGroupCancelled = true)
    }

    private fun fail(message: String) { stateFlow.value = stateFlow.value.copy(error = message) }
    private fun parseEndpoint(value: String): Pair<String, Int> {
        val endpoint = value.trim()
        val (address, explicitPort) = if (endpoint.startsWith("[")) {
            val closingBracket = endpoint.indexOf(']')
            require(closingBracket > 1 && endpoint.getOrNull(closingBracket + 1) == ':') { "Adresse IPv6 invalide." }
            endpoint.substring(1, closingBracket) to endpoint.substring(closingBracket + 2)
        } else {
            val parts = endpoint.split(":", limit = 2)
            parts[0] to parts.getOrNull(1)
        }
        require(address.isNotBlank()) { "Adresse IP requise." }
        val port = explicitPort?.toIntOrNull() ?: LocalProtocol.DEFAULT_PORT
        require(port in 1..65535) { "Port invalide." }
        return address to port
    }
    private fun writer(socket: Socket) = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private fun reader(socket: Socket) = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private fun read(reader: BufferedReader): Message { val line = reader.readLine() ?: error("Connexion fermée."); return LocalProtocol.decode(line).getOrElse { throw IllegalArgumentException(it.message) } }
    private fun send(writer: BufferedWriter, message: Message) = synchronized(writer) { writer.write(LocalProtocol.encode(message)); writer.newLine(); writer.flush() }
    private fun WireParticipant.toParticipant() = Participant(id, name, if (isHost) ParticipantRole.HOST else ParticipantRole.PARTICIPANT)
    private fun Throwable.userMessage() = message?.take(120) ?: "erreur réseau"

    private data class OutgoingAudio(val speakerId: String, val isHost: Boolean, val pcm: ByteArray)
    private class Peer(val id: String, val name: String, val socket: Socket, val writer: BufferedWriter, var lastSeen: Long = System.currentTimeMillis()) { fun close() = runCatching { socket.close() } }
    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 35_000
        const val HEARTBEAT_MS = 10_000L
        const val HOST_DISCONNECTED_MESSAGE = "L’hôte s’est déconnecté : le groupe a été annulé."
        const val PTT_EXPIRY_CHECK_MS = 500L
        const val UDP_PORT = 49_322
        const val MAX_CLIENTS = 8
        const val UDP_BUFFER_BYTES = 64 * 1024
        const val ACCEPT_THROTTLE_MS = 25L
        const val ACCEPT_FAILURE_BACKOFF_MS = 250L
    }
}
