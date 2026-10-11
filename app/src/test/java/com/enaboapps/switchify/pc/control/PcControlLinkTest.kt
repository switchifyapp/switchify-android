package com.enaboapps.switchify.pc.control

import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcControlLinkTest {
    @Test
    fun matchesOnlyThePcControlLink() {
        assertTrue(PcControlLink.matches("switchify", "pc-control"))
        assertFalse(PcControlLink.matches("switchify-remote", "remote"))
        assertFalse(PcControlLink.matches("switchify", "settings"))
        assertFalse(PcControlLink.matches(null, null))
    }

    @Test
    fun readsEverySurfaceAndIgnoresUnknownValues() {
        PcRemoteSurface.entries.forEach { assertEquals(it, PcControlLink.surface(it.storageKey)) }
        assertNull(PcControlLink.surface("keyboard"))
        assertNull(PcControlLink.surface(null))
    }

    @Test
    fun buildsLinksThatRoundTrip() {
        assertEquals("switchify://pc-control?surface=mouse", PcControlLink.uri(PcRemoteSurface.Mouse))
        assertEquals("switchify://pc-control?surface=forwarding", PcControlLink.uri(PcRemoteSurface.Forwarding))
        assertEquals("switchify://pc-control", PcControlLink.uri(null))
    }

    @Test
    fun aRequestIsConsumedOnlyOnce() {
        PcControlRequests.request(PcRemoteSurface.Forwarding)
        PcControlRequests.consume(PcRemoteSurface.Mouse)
        assertEquals(PcRemoteSurface.Forwarding, PcControlRequests.surface.value)
        PcControlRequests.consume(PcRemoteSurface.Forwarding)
        assertNull(PcControlRequests.surface.value)
    }
}
