package com.talkiwalki.junior.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class UdpVoiceCodecTest {
    @Test fun packetRoundTrip() {
        val packet = UdpVoicePacket("client-1", 42, byteArrayOf(1, 2, 3))
        val decoded = UdpVoiceCodec.decode(UdpVoiceCodec.encode(packet), UdpVoiceCodec.encode(packet).size)!!
        assertEquals(packet.speakerId, decoded.speakerId); assertEquals(packet.sequence, decoded.sequence); assertTrue(packet.pcm.contentEquals(decoded.pcm))
    }
    @Test fun invalidPacketIsRejected() { assertNull(UdpVoiceCodec.decode(byteArrayOf(1, 2), 2)) }
}
