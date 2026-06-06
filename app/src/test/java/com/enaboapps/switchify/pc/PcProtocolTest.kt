package com.enaboapps.switchify.pc

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcProtocolTest {
    @Test
    fun buildsPairingRequest() {
        val json = JSONObject(
            PcProtocol.pairingRequest(
                id = "android-request-id",
                deviceId = "android-device",
                deviceName = "Android phone",
                desktopId = "desktop-1",
                requestNonce = "nonce"
            )
        )

        assertEquals(1, json.getInt("version"))
        assertEquals("android-request-id", json.getString("id"))
        assertEquals("pairing.request", json.getString("type"))
        assertEquals("android-device", json.getJSONObject("payload").getString("deviceId"))
        assertEquals("Android phone", json.getJSONObject("payload").getString("deviceName"))
        assertEquals("desktop-1", json.getJSONObject("payload").getString("desktopId"))
        assertEquals("nonce", json.getJSONObject("payload").getString("requestNonce"))
    }

    @Test
    fun parsesAndValidatesPairingComplete() {
        val response = PcProtocol.parseResponse(
            """
            {"version":1,"id":"req-1","type":"pairing.complete","ok":true,"payload":{"desktopId":"desktop-1","deviceId":"device-1","token":"token"},"error":null}
            """.trimIndent()
        ) as PcProtocolResponse.PairingComplete

        assertTrue(PcProtocol.validatePairingComplete(response, "desktop-1", "device-1"))
        assertFalse(PcProtocol.validatePairingComplete(response, "desktop-2", "device-1"))
        assertFalse(PcProtocol.validatePairingComplete(response, "desktop-1", "device-2"))
    }

    @Test
    fun mapsKnownPairingErrors() {
        assertEquals("Request rejected.", PcProtocol.userMessageForError("pairing_rejected"))
        assertEquals("Request expired. Try again.", PcProtocol.userMessageForError("pairing_request_expired"))
    }

    @Test
    fun stableStringifySortsObjectKeys() {
        val payload = JSONObject().put("z", 1).put("a", true)

        assertEquals("""{"a":true,"z":1}""", PcProtocol.stableStringify(payload))
    }

    @Test
    fun authProofMatchesProtocolFixture() {
        val proof = PcProtocol.authProof(
            id = "req-1",
            deviceId = "device-1",
            timestamp = 1000L,
            type = "connection.ping",
            payload = JSONObject(),
            token = "shared-token"
        )

        assertEquals("o0CHNFgcFjPSL1PaWzBP6riHHr9V8kxo-qTKAPUvT7A", proof)
    }
}
