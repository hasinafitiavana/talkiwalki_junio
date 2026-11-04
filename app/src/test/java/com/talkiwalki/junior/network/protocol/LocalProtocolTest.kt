package com.talkiwalki.junior.network.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProtocolTest {
    @Test fun participants_roundTripPreservesValues() {
        val message = LocalProtocol.ParticipantsUpdated(
            listOf(LocalProtocol.WireParticipant("host-1", "Camille", true), LocalProtocol.WireParticipant("client-2", "Noah", false))
        )

        val decoded = LocalProtocol.decode(LocalProtocol.encode(message)).getOrThrow()

        assertEquals(message, decoded)
    }

    @Test fun unsupportedVersionIsRejected() {
        assertTrue(LocalProtocol.decode("9|HEARTBEAT").isFailure)
    }

    @Test fun oversizedNicknameIsRejected() {
        val encodedName = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("a".repeat(33).toByteArray())
        assertTrue(LocalProtocol.decode("1|JOIN|$encodedName").isFailure)
    }
}
