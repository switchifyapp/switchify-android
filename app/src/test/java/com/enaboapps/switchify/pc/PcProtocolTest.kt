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

    @Test
    fun buildsPointerProfileCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.pointerProfile(
                id = "profile-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L
            )
        )

        assertEquals(1, json.getInt("version"))
        assertEquals("profile-1", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("pointer.profile", json.getString("type"))
        assertEquals(0, json.getJSONObject("payload").length())
        assertEquals("6g3iFCFoeVuM016G2evT0XlnNMgzqd3lflUYPNfHuI8", json.getString("auth"))
        assertFalse(json.toString().contains("shared-token"))
    }

    @Test
    fun parsesPointerProfileResponse() {
        val response = PcProtocol.parseResponse(validPointerProfileResponse()) as PcProtocolResponse.PointerProfile

        assertEquals("profile-1", response.id)
        assertEquals("0:0:1280:720:1.5", response.profile.displayId)
        assertEquals(1.5, response.profile.scaleFactor, 0.0)
        assertEquals(1280, response.profile.bounds.width)
        assertEquals(500, response.profile.maxDelta)
        assertEquals(130, response.profile.recommendedDeltas.medium)
    }

    @Test
    fun rejectsMalformedPointerProfileResponses() {
        assertEquals(
            PcProtocolResponse.Invalid,
            PcProtocol.parseResponse(validPointerProfileResponse(displayId = ""))
        )
        assertEquals(
            PcProtocolResponse.Invalid,
            PcProtocol.parseResponse(validPointerProfileResponse(scaleFactor = 0.0))
        )
        assertEquals(
            PcProtocolResponse.Invalid,
            PcProtocol.parseResponse(validPointerProfileResponse(width = 0))
        )
        assertEquals(
            PcProtocolResponse.Invalid,
            PcProtocol.parseResponse(
                """
                {"version":1,"id":"profile-1","type":"pointer.profile","ok":true,"payload":{"displayId":"0:0:1280:720:1.5","scaleFactor":1.5,"bounds":{"x":0,"y":0,"width":1280,"height":720},"maxDelta":500},"error":null}
                """.trimIndent()
            )
        )
    }

    @Test
    fun buildsMouseMoveCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.mouseMove(
                id = "req-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                dx = 80,
                dy = -80
            )
        )

        assertEquals(1, json.getInt("version"))
        assertEquals("req-1", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("mouse.move", json.getString("type"))
        assertEquals(80, json.getJSONObject("payload").getInt("dx"))
        assertEquals(-80, json.getJSONObject("payload").getInt("dy"))
        assertEquals("H2mzYjrSRbwKsOiNk7s193qUijYSNqhoxLD92Vp2QDA", json.getString("auth"))
        assertFalse(json.toString().contains("shared-token"))
    }

    @Test
    fun buildsMouseClickAndScrollCommands() {
        val click = JSONObject(PcProtocol.mouseClick("click-1", "device-1", "token", 1000L))
        val doubleClick = JSONObject(PcProtocol.mouseDoubleClick("double-1", "device-1", "token", 1000L))
        val rightClick = JSONObject(PcProtocol.mouseRightClick("right-1", "device-1", "token", 1000L))
        val scroll = JSONObject(PcProtocol.mouseScroll("scroll-1", "device-1", "token", 1000L, 0, 5))

        assertEquals("mouse.click", click.getString("type"))
        assertEquals("left", click.getJSONObject("payload").getString("button"))
        assertEquals("mouse.doubleClick", doubleClick.getString("type"))
        assertEquals("left", doubleClick.getJSONObject("payload").getString("button"))
        assertEquals("mouse.rightClick", rightClick.getString("type"))
        assertEquals(0, rightClick.getJSONObject("payload").length())
        assertEquals("mouse.scroll", scroll.getString("type"))
        assertEquals(5, scroll.getJSONObject("payload").getInt("dy"))
    }

    @Test
    fun buildsKeyboardTypeTextCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.keyboardTypeText(
                id = "type-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                text = "Hello café 👋"
            )
        )

        assertEquals(1, json.getInt("version"))
        assertEquals("type-1", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("keyboard.typeText", json.getString("type"))
        assertEquals("Hello café 👋", json.getJSONObject("payload").getString("text"))
        assertEquals(
            PcProtocol.authProof(
                id = "type-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.typeText",
                payload = JSONObject().put("text", "Hello café 👋"),
                token = "shared-token"
            ),
            json.getString("auth")
        )
        assertFalse(json.toString().contains("shared-token"))
    }

    private fun validPointerProfileResponse(
        displayId: String = "0:0:1280:720:1.5",
        scaleFactor: Double = 1.5,
        width: Int = 1280
    ): String {
        return """
            {"version":1,"id":"profile-1","type":"pointer.profile","ok":true,"payload":{"displayId":"$displayId","scaleFactor":$scaleFactor,"bounds":{"x":0,"y":0,"width":$width,"height":720},"maxDelta":500,"recommendedDeltas":{"small":50,"medium":130,"large":252}},"error":null}
        """.trimIndent()
    }
}
