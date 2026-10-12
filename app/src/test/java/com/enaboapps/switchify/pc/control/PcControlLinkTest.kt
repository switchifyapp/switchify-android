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
        assertEquals("switchify://pc-control?surface=forwarding&start=forwarding", PcControlLink.uri(PcRemoteSurface.Forwarding, startForwarding = true))
        assertEquals("switchify://pc-control", PcControlLink.uri(null))
        assertEquals("switchify://pc-control?pc=desk-1", PcControlLink.uri(null, "desk-1"))
        assertEquals("switchify://pc-control?surface=mouse&pc=a%26b", PcControlLink.uri(PcRemoteSurface.Mouse, "a&b"))
    }

    @Test
    fun ignoresBlankDesktopIds() {
        assertEquals("desk-1", PcControlLink.desktopId("desk-1"))
        assertNull(PcControlLink.desktopId(" "))
        assertNull(PcControlLink.desktopId(null))
    }

    @Test
    fun aDesktopRequestIsConsumedOnlyOnce() {
        PcControlRequests.requestDesktop("desk-2")
        PcControlRequests.consumeDesktop("desk-1")
        assertEquals("desk-2", PcControlRequests.desktop.value)
        PcControlRequests.consumeDesktop("desk-2")
        assertNull(PcControlRequests.desktop.value)
    }

    @Test
    fun aForwardingStartRequestIsConsumedOnlyOnce() {
        assertTrue(PcControlLink.startsForwarding("forwarding"))
        assertFalse(PcControlLink.startsForwarding(null))
        PcControlRequests.requestForwardingStart(true)
        assertTrue(PcControlRequests.consumeForwardingStart())
        assertFalse(PcControlRequests.consumeForwardingStart())
        PcControlRequests.requestForwardingStart(true)
        PcControlRequests.requestForwardingStart(false)
        assertFalse(PcControlRequests.consumeForwardingStart())
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
