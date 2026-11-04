package com.talkiwalki.junior.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrGroupInvitationTest {
    @Test fun `parses invitation created by this app`() {
        assertEquals("192.168.1.42:49321", QrGroupInvitation.parse(QrGroupInvitation.create("192.168.1.42", 49321)))
    }

    @Test fun `keeps an IPv6 endpoint usable by the socket`() {
        assertEquals("[fe80::42]:49321", QrGroupInvitation.parse(QrGroupInvitation.create("fe80::42", 49321)))
    }

    @Test fun `rejects malformed invitation`() {
        assertNull(QrGroupInvitation.parse("https://example.com"))
        assertNull(QrGroupInvitation.parse("talkiwalki-junior://join?host=192.168.1.42&port=0"))
    }
}
