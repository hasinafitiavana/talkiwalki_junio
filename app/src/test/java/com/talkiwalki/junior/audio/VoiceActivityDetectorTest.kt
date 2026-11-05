package com.talkiwalki.junior.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivityDetectorTest {
    @Test fun silentFrame_isNotVoice() {
        assertFalse(VoiceActivityDetector.hasVoice(ByteArray(640)))
    }

    @Test fun audiblePcmFrame_isVoice() {
        val frame = ByteArray(640)
        frame.indices.step(2).forEach { index ->
            frame[index] = 0xE8.toByte()
            frame[index + 1] = 0x03
        }

        assertTrue(VoiceActivityDetector.hasVoice(frame))
    }
}
