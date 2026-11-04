package com.talkiwalki.junior.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PttArbiterTest {
    @Test fun oneSpeakerAtATimeAndDisconnectReleasesLease() {
        val arbiter = PttArbiter()
        assertTrue(arbiter.request("a")); assertFalse(arbiter.request("b"))
        assertTrue(arbiter.disconnect("a")); assertTrue(arbiter.request("b"))
    }
    @Test fun expiredLeaseCanBeTaken() {
        var now = 0L; val arbiter = PttArbiter(100) { now }
        arbiter.request("a"); now = 100
        assertEquals(null, arbiter.currentSpeaker()); assertTrue(arbiter.request("b"))
    }
}
