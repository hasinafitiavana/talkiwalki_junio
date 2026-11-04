package com.talkiwalki.junior.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveredGroupTest {
    @Test fun `formats IPv6 endpoint with brackets`() {
        val group = DiscoveredGroup("service", "Groupe", "fe80::42", 49321)

        assertEquals("[fe80::42]:49321", group.endpoint)
    }
}
