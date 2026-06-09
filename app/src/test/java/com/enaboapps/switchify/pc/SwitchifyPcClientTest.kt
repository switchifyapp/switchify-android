package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwitchifyPcClientTest {
    @Test
    fun invalidResponseIsSkippedInsteadOfReturned() {
        assertNull(resolveExpectedResponse(PcProtocolResponse.Invalid, "req-1"))
    }

    @Test
    fun ackForMatchingRequestIdIsReturned() {
        val ack = PcProtocolResponse.Ack("req-1")
        assertEquals(ack, resolveExpectedResponse(ack, "req-1"))
    }

    @Test
    fun ackForOtherRequestIdIsSkipped() {
        assertNull(resolveExpectedResponse(PcProtocolResponse.Ack("req-2"), "req-1"))
    }

    @Test
    fun pairingCompleteForMatchingRequestIdIsReturned() {
        val pairing = PcProtocolResponse.PairingComplete("req-1", "desktop", "device", "token")
        assertEquals(pairing, resolveExpectedResponse(pairing, "req-1"))
    }

    @Test
    fun errorWithMatchingIdIsReturned() {
        val error = PcProtocolResponse.Error("req-1", "invalid_auth", "invalid")
        assertEquals(error, resolveExpectedResponse(error, "req-1"))
    }

    @Test
    fun errorWithNullIdIsReturned() {
        val error = PcProtocolResponse.Error(null, "invalid_auth", "invalid")
        assertEquals(error, resolveExpectedResponse(error, "req-1"))
    }

    @Test
    fun errorForOtherRequestIdIsSkipped() {
        assertNull(resolveExpectedResponse(PcProtocolResponse.Error("req-2", "code", "message"), "req-1"))
    }

    @Test
    fun garbageBeforeAckDoesNotFailTheCommand() {
        val frames = listOf(
            PcProtocol.parseResponse("not json at all"),
            PcProtocol.parseResponse("{\"type\":\"unknown\"}"),
            PcProtocol.parseResponse("{\"type\":\"ack\",\"ok\":true,\"id\":\"req-1\"}")
        )

        val resolved = frames.firstNotNullOfOrNull { resolveExpectedResponse(it, "req-1") }

        assertEquals(PcProtocolResponse.Ack("req-1"), resolved)
    }
}
