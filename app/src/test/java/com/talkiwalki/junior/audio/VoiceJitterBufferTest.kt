package com.talkiwalki.junior.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceJitterBufferTest {
    @Test fun packetsArrivingOutOfOrder_arePlayedInSequence() {
        val buffer = VoiceJitterBuffer(frameBytes = 2)
        buffer.offer(UdpVoicePacket("speaker", 10, byteArrayOf(10, 10)))
        buffer.offer(UdpVoicePacket("speaker", 12, byteArrayOf(12, 12)))
        buffer.offer(UdpVoicePacket("speaker", 11, byteArrayOf(11, 11)))

        assertArrayEquals(byteArrayOf(10, 10), buffer.nextFrame())
        assertArrayEquals(byteArrayOf(11, 11), buffer.nextFrame())
        assertArrayEquals(byteArrayOf(12, 12), buffer.nextFrame())
    }

    @Test fun missingPacket_becomesOneSilentFrameWithoutDelayingFollowingAudio() {
        val buffer = VoiceJitterBuffer(frameBytes = 2)
        buffer.offer(UdpVoicePacket("speaker", 1, byteArrayOf(1, 1)))
        buffer.offer(UdpVoicePacket("speaker", 3, byteArrayOf(3, 3)))

        assertArrayEquals(byteArrayOf(1, 1), buffer.nextFrame())
        assertTrue(buffer.nextFrame().all { it == 0.toByte() })
        assertArrayEquals(byteArrayOf(3, 3), buffer.nextFrame())
    }
}
