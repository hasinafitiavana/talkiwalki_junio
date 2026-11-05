package com.talkiwalki.junior.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerStartDetectorTest {
    @Test
    fun remoteSpeakerStart_alertsOnlyTheListener() {
        val detector = SpeakerStartDetector()

        assertFalse(detector.anotherParticipantStartedSpeaking("listener", null))
        assertTrue(detector.anotherParticipantStartedSpeaking("listener", "speaker"))
        assertFalse(detector.anotherParticipantStartedSpeaking("listener", "speaker"))
    }

    @Test
    fun localSpeakerStart_doesNotAlert() {
        val detector = SpeakerStartDetector()

        assertFalse(detector.anotherParticipantStartedSpeaking("speaker", null))
        assertFalse(detector.anotherParticipantStartedSpeaking("speaker", "speaker"))
    }

    @Test
    fun existingSpeaker_doesNotAlertWhenJoiningSession() {
        val detector = SpeakerStartDetector()

        assertFalse(detector.anotherParticipantStartedSpeaking("listener", "speaker"))
    }
}
