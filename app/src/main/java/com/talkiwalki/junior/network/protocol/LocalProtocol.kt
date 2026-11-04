package com.talkiwalki.junior.network.protocol

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Line-delimited, versioned protocol used only on the local Wi-Fi network. */
object LocalProtocol {
    const val VERSION = 2
    const val MAX_LINE_BYTES = 8 * 1024
    const val DEFAULT_PORT = 49_321

    sealed interface Message { val type: String }
    data class Join(val nickname: String) : Message { override val type = "JOIN" }
    data class JoinAccepted(val sessionId: String, val groupName: String, val participantId: String) : Message { override val type = "JOIN_ACCEPTED" }
    data class JoinRejected(val reason: String) : Message { override val type = "JOIN_REJECTED" }
    data class ParticipantsUpdated(val participants: List<WireParticipant>) : Message { override val type = "PARTICIPANTS_UPDATED" }
    data object Leave : Message { override val type = "LEAVE" }
    data object Heartbeat : Message { override val type = "HEARTBEAT" }
    data object PttRequest : Message { override val type = "PTT_REQUEST" }
    data object PttRelease : Message { override val type = "PTT_RELEASE" }
    data class PttGranted(val speakerId: String) : Message { override val type = "PTT_GRANTED" }
    data class PttDenied(val speakerId: String?) : Message { override val type = "PTT_DENIED" }
    data class PttState(val speakerId: String?) : Message { override val type = "PTT_STATE" }
    data class UdpRegister(val port: Int) : Message { override val type = "UDP_REGISTER" }
    data class Error(val reason: String) : Message { override val type = "ERROR" }

    data class WireParticipant(val id: String, val name: String, val isHost: Boolean)

    fun encode(message: Message): String {
        val fields = when (message) {
            is Join -> listOf(message.nickname)
            is JoinAccepted -> listOf(message.sessionId, message.groupName, message.participantId)
            is JoinRejected -> listOf(message.reason)
            is ParticipantsUpdated -> message.participants.flatMap { listOf(it.id, it.name, if (it.isHost) "1" else "0") }
            Leave, Heartbeat, PttRequest, PttRelease -> emptyList()
            is PttGranted -> listOf(message.speakerId)
            is PttDenied -> listOf(message.speakerId ?: "")
            is PttState -> listOf(message.speakerId ?: "")
            is UdpRegister -> listOf(message.port.toString())
            is Error -> listOf(message.reason)
        }
        return listOf(VERSION.toString(), message.type, *fields.map(::encodeField).toTypedArray()).joinToString("|")
    }

    fun decode(line: String): Result<Message> = runCatching {
        require(line.toByteArray(StandardCharsets.UTF_8).size <= MAX_LINE_BYTES) { "Message trop volumineux." }
        val fields = line.split('|')
        require(fields.size >= 2 && fields[0].toIntOrNull() == VERSION) { "Version de protocole non compatible." }
        fun value(index: Int): String = decodeField(fields.getOrNull(index) ?: error("Message incomplet."))
        when (fields[1]) {
            "JOIN" -> Join(value(2).validatedName())
            "JOIN_ACCEPTED" -> JoinAccepted(value(2).validatedToken(), value(3).validatedName(), value(4).validatedToken())
            "JOIN_REJECTED" -> JoinRejected(value(2).validatedReason())
            "PARTICIPANTS_UPDATED" -> {
                val body = fields.drop(2)
                require(body.size % 3 == 0 && body.size <= 96) { "Liste de participants invalide." }
                ParticipantsUpdated(body.chunked(3).map { WireParticipant(decodeField(it[0]).validatedToken(), decodeField(it[1]).validatedName(), decodeField(it[2]) == "1") })
            }
            "LEAVE" -> Leave
            "HEARTBEAT" -> Heartbeat
            "PTT_REQUEST" -> PttRequest
            "PTT_RELEASE" -> PttRelease
            "PTT_GRANTED" -> PttGranted(value(2).validatedToken())
            "PTT_DENIED" -> PttDenied(value(2).ifBlank { null }?.validatedToken())
            "PTT_STATE" -> PttState(value(2).ifBlank { null }?.validatedToken())
            "UDP_REGISTER" -> UdpRegister(value(2).toInt().also { require(it in 1..65535) })
            "ERROR" -> Error(value(2).validatedReason())
            else -> error("Type de message inconnu.")
        }
    }

    private fun encodeField(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun decodeField(value: String) = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    private fun String.validatedName(): String { require(length in 1..32) { "Pseudo ou groupe invalide." }; return this }
    private fun String.validatedToken(): String { require(length in 1..64) { "Identifiant invalide." }; return this }
    private fun String.validatedReason(): String { require(length in 1..120) { "Erreur invalide." }; return this }
}
