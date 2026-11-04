package com.talkiwalki.junior.domain.model

/** Identifies a local talkie-walkie session without exposing transport details. */
@JvmInline
value class SessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "A session ID cannot be blank." }
    }
}

enum class ParticipantRole {
    HOST,
    PARTICIPANT
}

data class Participant(
    val id: String,
    val displayName: String,
    val role: ParticipantRole
)
