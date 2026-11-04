package com.talkiwalki.junior.network

import com.talkiwalki.junior.core.coroutines.DefaultDispatcherProvider
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
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val error: String? = null
)

/** Owns one local TCP session. It never performs service discovery or Internet I/O. */
class LocalSessionRepository(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    private val scope: CoroutineScope = CoroutineScope(DefaultDispatcherProvider.io)
) {
    private val stateFlow = MutableStateFlow(LocalSessionState())
    val state: StateFlow<LocalSessionState> = stateFlow.asStateFlow()
    private val lock = Mutex()
    private val peerSlots = Semaphore(MAX_CLIENTS)
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null
    private var heartbeatJob: Job? = null
    private var clientSocket: Socket? = null
    private var clientWriter: BufferedWriter? = null
    private var clientReaderJob: Job? = null
    private var sessionId = ""
    private var isHost = false
    private val peers = linkedMapOf<String, Peer>()

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
            stateFlow.value = LocalSessionState(groupName, LocalNetworkInfo.localIpv4Address(), socket.localPort, listOf(host))
            acceptJob = scope.launch { acceptLoop(socket) }
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
                    stateFlow.value = LocalSessionState(first.groupName, address, port)
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
            clientReaderJob?.cancel(); clientReaderJob = null
            runCatching { clientWriter?.let { send(it, LocalProtocol.Leave) } }
            runCatching { clientSocket?.close() }; clientSocket = null; clientWriter = null
            acceptJob?.cancel(); acceptJob = null
            runCatching { server?.close() }; server = null
            peers.values.forEach { it.close() }; peers.clear()
            isHost = false; sessionId = ""
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
                send(writer, LocalProtocol.JoinAccepted(sessionId, stateFlow.value.groupName))
                broadcastParticipants()
            }
            while (scope.isActive) {
                when (read(reader)) {
                    LocalProtocol.Leave -> break
                    LocalProtocol.Heartbeat -> peer.lastSeen = System.currentTimeMillis()
                    else -> send(writer, LocalProtocol.Error("Message non autorisé."))
                }
            }
        } catch (_: Exception) {
            // Socket closure and malformed remote input both result in removing this peer.
        } finally {
            lock.withLock {
                peers.entries.removeIf { it.value.socket == socket }
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
                else -> Unit
            }
        } catch (_: Exception) {
            if (clientSocket == socket) fail("Connexion au groupe interrompue.")
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && clientWriter != null) {
                delay(HEARTBEAT_MS)
                runCatching { clientWriter?.let { send(it, LocalProtocol.Heartbeat) } }.onFailure { fail("Connexion au groupe interrompue.") }
            }
        }
    }

    private suspend fun broadcastParticipants() {
        val host = stateFlow.value.participants.firstOrNull { it.role == ParticipantRole.HOST }
        val participants = listOfNotNull(host) + peers.values.map { Participant(it.id, it.name, ParticipantRole.PARTICIPANT) }
        stateFlow.value = stateFlow.value.copy(participants = participants)
        val message = LocalProtocol.ParticipantsUpdated(participants.map { WireParticipant(it.id, it.displayName, it.role == ParticipantRole.HOST) })
        peers.values.toList().forEach { peer -> runCatching { send(peer.writer, message) }.onFailure { peers.remove(peer.id); peer.close() } }
    }

    private fun fail(message: String) { stateFlow.value = stateFlow.value.copy(error = message) }
    private fun parseEndpoint(value: String): Pair<String, Int> {
        val parts = value.trim().split(":", limit = 2)
        require(parts[0].isNotBlank()) { "Adresse IP requise." }
        val port = parts.getOrNull(1)?.toIntOrNull() ?: LocalProtocol.DEFAULT_PORT
        require(port in 1..65535) { "Port invalide." }
        return parts[0] to port
    }
    private fun writer(socket: Socket) = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private fun reader(socket: Socket) = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private fun read(reader: BufferedReader): Message { val line = reader.readLine() ?: error("Connexion fermée."); return LocalProtocol.decode(line).getOrElse { throw IllegalArgumentException(it.message) } }
    private fun send(writer: BufferedWriter, message: Message) = synchronized(writer) { writer.write(LocalProtocol.encode(message)); writer.newLine(); writer.flush() }
    private fun WireParticipant.toParticipant() = Participant(id, name, if (isHost) ParticipantRole.HOST else ParticipantRole.PARTICIPANT)
    private fun Throwable.userMessage() = message?.take(120) ?: "erreur réseau"

    private class Peer(val id: String, val name: String, val socket: Socket, val writer: BufferedWriter, var lastSeen: Long = System.currentTimeMillis()) { fun close() = runCatching { socket.close() } }
    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 35_000
        const val HEARTBEAT_MS = 10_000L
        const val MAX_CLIENTS = 8
        const val ACCEPT_THROTTLE_MS = 25L
        const val ACCEPT_FAILURE_BACKOFF_MS = 250L
    }
}
