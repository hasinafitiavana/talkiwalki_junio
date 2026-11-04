package com.talkiwalki.junior.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveredGroupRegistryTest {
    @Test fun duplicateServiceReplacesPreviousResolution() {
        val registry = DiscoveredGroupRegistry()
        registry.add(DiscoveredGroup("svc", "Junior", "192.168.1.5", 49321))
        registry.add(DiscoveredGroup("svc", "Junior", "192.168.1.6", 49321))

        assertEquals(1, registry.values().size)
        assertEquals("192.168.1.6", registry.values().single().host)
    }

    @Test fun lostServiceIsRemoved() {
        val registry = DiscoveredGroupRegistry()
        registry.add(DiscoveredGroup("svc", "Junior", "192.168.1.5", 49321))
        registry.remove("svc")
        assertTrue(registry.values().isEmpty())
    }
}
