package com.enaboapps.switchify.pc

import org.json.JSONObject
import org.json.JSONArray
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
    fun parsesAckOnlyWhenOkIsTrue() {
        val response = PcProtocol.parseResponse(
            """{"version":1,"id":"req-1","type":"ack","ok":true,"error":null}"""
        )

        assertEquals(PcProtocolResponse.Ack("req-1"), response)
    }

    @Test
    fun rejectsAckWhenOkIsFalse() {
        val response = PcProtocol.parseResponse(
            """{"version":1,"id":"req-1","type":"ack","ok":false,"error":null}"""
        )

        assertEquals(PcProtocolResponse.Invalid, response)
    }

    @Test
    fun rejectsAckWhenOkIsMissing() {
        val response = PcProtocol.parseResponse(
            """{"version":1,"id":"req-1","type":"ack","error":null}"""
        )

        assertEquals(PcProtocolResponse.Invalid, response)
    }

    @Test
    fun rejectsAckWhenErrorIsPresent() {
        val response = PcProtocol.parseResponse(
            """{"version":1,"id":"req-1","type":"ack","ok":true,"error":{"code":"failed","message":"failed"}}"""
        )

        assertEquals(PcProtocolResponse.Invalid, response)
    }

    @Test
    fun mapsKnownPairingErrors() {
        assertEquals("Request rejected.", PcProtocol.userMessageForError("pairing_rejected"))
        assertEquals("Request expired. Try again.", PcProtocol.userMessageForError("pairing_request_expired"))
    }

    @Test
    fun mapsErrorMessagesToTypedReasons() {
        assertEquals(PcErrorReason.PairingRejected, PcProtocol.errorReason("pairing_rejected"))
        assertEquals(PcErrorReason.PairingRequestExpired, PcProtocol.errorReason("pairing_request_expired"))
        assertEquals(PcErrorReason.Failed, PcProtocol.errorReason("anything_else"))
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

        assertEquals("98bZHKWHa3ooOYZyXBuYpzOdbPWGW5FV04fEjxAl9sI", proof)
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
        assertEquals("lgHsjgfWbqbrE-ugPTIDzjxm5MjEdNRpbHQV9B4ZNPM", json.getString("auth"))
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
        assertFalse(response.profile.capabilities.noAckMouseMove)
        assertEquals(emptySet<String>(), response.profile.capabilities.noAckCommands)
        assertEquals(emptySet<String>(), response.profile.capabilities.supportedCommands)
        assertFalse(response.profile.capabilities.pointerSpeed.supported)
        assertEquals(130, response.profile.pointerMoveStep())
    }

    @Test
    fun parsesPointerProfileSpeedCapability() {
        val response = PcProtocol.parseResponse(
            validPointerProfileResponse(
                capabilities = ""","capabilities":{"pointerSpeed":{"supported":true,"scalePercent":125,"minScalePercent":25,"maxScalePercent":225,"stepPercent":5,"baseMoveDelta":128,"effectiveMoveDelta":160}}"""
            )
        ) as PcProtocolResponse.PointerProfile

        assertTrue(response.profile.capabilities.pointerSpeed.supported)
        assertEquals(125.0, response.profile.capabilities.pointerSpeed.scalePercent, 0.0)
        assertEquals(225.0, response.profile.capabilities.pointerSpeed.maxScalePercent, 0.0)
        assertEquals(128, response.profile.capabilities.pointerSpeed.baseMoveDelta)
        assertEquals(160, response.profile.capabilities.pointerSpeed.effectiveMoveDelta)
        assertEquals(128, response.profile.pointerMoveStep())
    }

    @Test
    fun rejectsMalformedPointerProfileSpeedCapability() {
        assertEquals(
            PcProtocolResponse.Invalid,
            PcProtocol.parseResponse(
                validPointerProfileResponse(
                    capabilities = ""","capabilities":{"pointerSpeed":{"supported":true,"scalePercent":300,"minScalePercent":25,"maxScalePercent":225,"stepPercent":5,"baseMoveDelta":128,"effectiveMoveDelta":160}}"""
                )
            )
        )
    }

    @Test
    fun parsesPointerProfileNoAckMovementCapability() {
        val response = PcProtocol.parseResponse(
            validPointerProfileResponse(capabilities = ""","capabilities":{"noAckMouseMove":true}""")
        ) as PcProtocolResponse.PointerProfile

        assertTrue(response.profile.capabilities.noAckMouseMove)
    }

    @Test
    fun parsesPointerProfileNoAckCommandCapabilities() {
        val response = PcProtocol.parseResponse(
            validPointerProfileResponse(
                capabilities = ""","capabilities":{"noAckMouseMove":true,"noAckCommands":["mouse.move","mouse.click","keyboard.typeText","unknown.command"]}"""
            )
        ) as PcProtocolResponse.PointerProfile

        assertTrue(response.profile.capabilities.noAckMouseMove)
        assertEquals(setOf("mouse.move", "mouse.click", "keyboard.typeText"), response.profile.capabilities.noAckCommands)
    }

    @Test
    fun parsesPointerProfileSupportedCommands() {
        val response = PcProtocol.parseResponse(
            validPointerProfileResponse(
                capabilities = ""","capabilities":{"supportedCommands":["keyboard.textStream.open","keyboard.textStream.chunk","keyboard.textStream.key","keyboard.textStream.close","keyboard.modifierDown","keyboard.modifierUp","unknown.command"]}"""
            )
        ) as PcProtocolResponse.PointerProfile

        assertEquals(
            setOf(
                "keyboard.textStream.open",
                "keyboard.textStream.chunk",
                "keyboard.textStream.key",
                "keyboard.textStream.close",
                "keyboard.modifierDown",
                "keyboard.modifierUp"
            ),
            response.profile.capabilities.supportedCommands
        )
        assertTrue(response.profile.supportsTextStreams())
        assertTrue(response.profile.supportsModifierToggle())
    }

    @Test
    fun textStreamsRequireAllSupportedCommands() {
        val response = PcProtocol.parseResponse(
            validPointerProfileResponse(
                capabilities = ""","capabilities":{"supportedCommands":["keyboard.textStream.open","keyboard.textStream.chunk","keyboard.textStream.close"]}"""
            )
        ) as PcProtocolResponse.PointerProfile

        assertFalse(response.profile.supportsTextStreams())
    }

    @Test
    fun rejectsMalformedPointerProfileNoAckCommands() {
        assertEquals(
            PcProtocolResponse.Invalid,
            PcProtocol.parseResponse(validPointerProfileResponse(capabilities = ""","capabilities":{"noAckCommands":"mouse.click"}"""))
        )
        assertEquals(
            PcProtocolResponse.Invalid,
            PcProtocol.parseResponse(validPointerProfileResponse(capabilities = ""","capabilities":{"noAckCommands":["mouse.click",1]}"""))
        )
    }

    @Test
    fun rejectsMalformedPointerProfileSupportedCommands() {
        assertEquals(
            PcProtocolResponse.Invalid,
            PcProtocol.parseResponse(validPointerProfileResponse(capabilities = ""","capabilities":{"supportedCommands":"keyboard.textStream.open"}"""))
        )
        assertEquals(
            PcProtocolResponse.Invalid,
            PcProtocol.parseResponse(validPointerProfileResponse(capabilities = ""","capabilities":{"supportedCommands":["keyboard.textStream.open",1]}"""))
        )
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
        assertFalse(json.has("responseMode"))
        assertEquals("UKF4IEa-XaDsoKEWmsUDy7jXNzv_YDFSb26yHG2pzhQ", json.getString("auth"))
        assertFalse(json.toString().contains("shared-token"))
    }

    @Test
    fun buildsNoResponseMouseMoveCommandWithSignedResponseMode() {
        val json = JSONObject(
            PcProtocol.mouseMove(
                id = "req-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                dx = 80,
                dy = -80,
                responseMode = PcCommandResponseMode.None
            )
        )

        assertEquals("none", json.getString("responseMode"))
        assertEquals(
            PcProtocol.authProof(
                id = "req-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "mouse.move",
                payload = json.getJSONObject("payload"),
                token = "shared-token",
                responseMode = PcCommandResponseMode.None
            ),
            json.getString("auth")
        )
    }

    @Test
    fun buildsNoResponseControlCommandsWithSignedResponseMode() {
        val commands = listOf(
            JSONObject(PcProtocol.mouseClick("click-1", "device-1", "shared-token", 1000L, responseMode = PcCommandResponseMode.None)),
            JSONObject(PcProtocol.mouseDoubleClick("double-1", "device-1", "shared-token", 1000L, responseMode = PcCommandResponseMode.None)),
            JSONObject(PcProtocol.mouseRightClick("right-1", "device-1", "shared-token", 1000L, PcCommandResponseMode.None)),
            JSONObject(PcProtocol.mouseScroll("scroll-1", "device-1", "shared-token", 1000L, 0, 5, PcCommandResponseMode.None)),
            JSONObject(PcProtocol.mouseDragStart("drag-start-1", "device-1", "shared-token", 1000L, responseMode = PcCommandResponseMode.None)),
            JSONObject(PcProtocol.mouseDragEnd("drag-end-1", "device-1", "shared-token", 1000L, responseMode = PcCommandResponseMode.None)),
            JSONObject(PcProtocol.keyboardTypeText("type-1", "device-1", "shared-token", 1000L, "Hello", PcCommandResponseMode.None)),
            JSONObject(PcProtocol.keyboardTextStreamChar("stream-char-1", "device-1", "shared-token", 1000L, "android-1", 0, "H")),
            JSONObject(PcProtocol.keyboardTextStreamKey("stream-key-1", "device-1", "shared-token", 1000L, "android-1", 1, PcKeyboardKey.Enter)),
            JSONObject(PcProtocol.keyboardKey("key-1", "device-1", "shared-token", 1000L, PcKeyboardKey.Enter, PcCommandResponseMode.None)),
            JSONObject(PcProtocol.keyboardShortcut("shortcut-1", "device-1", "shared-token", 1000L, listOf(PcKeyboardShortcutKey.Meta), PcCommandResponseMode.None)),
            JSONObject(PcProtocol.keyboardModifierDown("modifier-down-1", "device-1", "shared-token", 1000L, PcKeyboardModifierKey.Ctrl, PcCommandResponseMode.None)),
            JSONObject(PcProtocol.keyboardModifierUp("modifier-up-1", "device-1", "shared-token", 1000L, PcKeyboardModifierKey.Ctrl, PcCommandResponseMode.None)),
            JSONObject(PcProtocol.windowControl("window-1", "device-1", "shared-token", 1000L, PcWindowControlAction.SwitchNext, PcCommandResponseMode.None))
        )

        for (json in commands) {
            assertEquals("none", json.getString("responseMode"))
            assertEquals(
                PcProtocol.authProof(
                    id = json.getString("id"),
                    deviceId = "device-1",
                    timestamp = 1000L,
                    type = json.getString("type"),
                    payload = json.getJSONObject("payload"),
                    token = "shared-token",
                    responseMode = PcCommandResponseMode.None
                ),
                json.getString("auth")
            )
        }
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
    fun buildsMouseDragStartCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.mouseDragStart(
                id = "drag-start-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L
            )
        )

        assertEquals(1, json.getInt("version"))
        assertEquals("drag-start-1", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("mouse.dragStart", json.getString("type"))
        assertEquals("left", json.getJSONObject("payload").getString("button"))
        assertEquals(
            PcProtocol.authProof(
                id = "drag-start-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "mouse.dragStart",
                payload = JSONObject().put("button", "left"),
                token = "shared-token"
            ),
            json.getString("auth")
        )
        assertFalse(json.toString().contains("shared-token"))
    }

    @Test
    fun buildsMouseDragEndCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.mouseDragEnd(
                id = "drag-end-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L
            )
        )

        assertEquals(1, json.getInt("version"))
        assertEquals("drag-end-1", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("mouse.dragEnd", json.getString("type"))
        assertEquals("left", json.getJSONObject("payload").getString("button"))
        assertEquals(
            PcProtocol.authProof(
                id = "drag-end-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "mouse.dragEnd",
                payload = JSONObject().put("button", "left"),
                token = "shared-token"
            ),
            json.getString("auth")
        )
        assertFalse(json.toString().contains("shared-token"))
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

    @Test
    fun buildsKeyboardKeyCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.keyboardKey(
                id = "key-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                key = PcKeyboardKey.Enter
            )
        )

        assertEquals(1, json.getInt("version"))
        assertEquals("key-1", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("keyboard.key", json.getString("type"))
        assertEquals("Enter", json.getJSONObject("payload").getString("key"))
        assertEquals(
            PcProtocol.authProof(
                id = "key-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.key",
                payload = JSONObject().put("key", "Enter"),
                token = "shared-token"
            ),
            json.getString("auth")
        )
        assertFalse(json.toString().contains("shared-token"))
    }

    @Test
    fun buildsMetaKeyboardKeyCommand() {
        val json = JSONObject(
            PcProtocol.keyboardKey(
                id = "key-meta",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                key = PcKeyboardKey.Meta
            )
        )

        assertEquals(1, json.getInt("version"))
        assertEquals("key-meta", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("keyboard.key", json.getString("type"))
        assertEquals("Meta", json.getJSONObject("payload").getString("key"))
        assertEquals(
            PcProtocol.authProof(
                id = "key-meta",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.key",
                payload = JSONObject().put("key", "Meta"),
                token = "shared-token"
            ),
            json.getString("auth")
        )
        assertFalse(json.toString().contains("shared-token"))
    }

    @Test
    fun buildsMetaKeyboardShortcutCommand() {
        val json = JSONObject(
            PcProtocol.keyboardShortcut(
                id = "shortcut-meta",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                keys = listOf(PcKeyboardShortcutKey.Meta)
            )
        )
        val payload = json.getJSONObject("payload")

        assertEquals(1, json.getInt("version"))
        assertEquals("shortcut-meta", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("keyboard.shortcut", json.getString("type"))
        assertEquals("Meta", payload.getJSONArray("keys").getString(0))
        assertEquals(
            PcProtocol.authProof(
                id = "shortcut-meta",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.shortcut",
                payload = JSONObject().put("keys", JSONArray(listOf("Meta"))),
                token = "shared-token"
            ),
            json.getString("auth")
        )
        assertFalse(json.toString().contains("shared-token"))
    }

    @Test
    fun shortcutKeysIncludeFullAlphabetInOrder() {
        assertEquals(
            ('A'..'Z').map { it.toString() },
            PC_SHORTCUT_LETTER_KEYS.map { it.protocolValue }
        )
    }

    @Test
    fun buildsMultiModifierAlphabetShortcutCommands() {
        val ctrlShiftA = JSONObject(
            PcProtocol.keyboardShortcut(
                id = "shortcut-ctrl-shift-a",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                keys = listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.Shift, PcKeyboardShortcutKey.A)
            )
        )
        val altF = JSONObject(
            PcProtocol.keyboardShortcut(
                id = "shortcut-alt-f",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                keys = listOf(PcKeyboardShortcutKey.Alt, PcKeyboardShortcutKey.F)
            )
        )
        val ctrlZ = JSONObject(
            PcProtocol.keyboardShortcut(
                id = "shortcut-ctrl-z",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                keys = listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.Z)
            )
        )

        assertEquals(listOf("Ctrl", "Shift", "A"), ctrlShiftA.getJSONObject("payload").getJSONArray("keys").asStringList())
        assertEquals(listOf("Alt", "F"), altF.getJSONObject("payload").getJSONArray("keys").asStringList())
        assertEquals(listOf("Ctrl", "Z"), ctrlZ.getJSONObject("payload").getJSONArray("keys").asStringList())
        assertEquals(
            PcProtocol.authProof(
                id = "shortcut-ctrl-shift-a",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.shortcut",
                payload = JSONObject().put("keys", JSONArray(listOf("Ctrl", "Shift", "A"))),
                token = "shared-token"
            ),
            ctrlShiftA.getString("auth")
        )
    }

    @Test
    fun buildsKeyboardModifierCommandsWithAuthProof() {
        val down = JSONObject(
            PcProtocol.keyboardModifierDown(
                id = "modifier-down-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                key = PcKeyboardModifierKey.Ctrl
            )
        )
        val up = JSONObject(
            PcProtocol.keyboardModifierUp(
                id = "modifier-up-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                key = PcKeyboardModifierKey.Ctrl
            )
        )

        assertEquals("keyboard.modifierDown", down.getString("type"))
        assertEquals("Ctrl", down.getJSONObject("payload").getString("key"))
        assertEquals(
            PcProtocol.authProof(
                id = "modifier-down-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.modifierDown",
                payload = JSONObject().put("key", "Ctrl"),
                token = "shared-token"
            ),
            down.getString("auth")
        )
        assertEquals("keyboard.modifierUp", up.getString("type"))
        assertEquals("Ctrl", up.getJSONObject("payload").getString("key"))
        assertEquals(
            PcProtocol.authProof(
                id = "modifier-up-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.modifierUp",
                payload = JSONObject().put("key", "Ctrl"),
                token = "shared-token"
            ),
            up.getString("auth")
        )
    }

    @Test
    fun buildsTextStreamOpenCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.keyboardTextStreamOpen(
                id = "stream-open-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                streamId = "android-123"
            )
        )

        assertEquals("keyboard.textStream.open", json.getString("type"))
        assertEquals("android-123", json.getJSONObject("payload").getString("streamId"))
        assertFalse(json.has("responseMode"))
        assertEquals(
            PcProtocol.authProof(
                id = "stream-open-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.textStream.open",
                payload = JSONObject().put("streamId", "android-123"),
                token = "shared-token"
            ),
            json.getString("auth")
        )
    }

    @Test
    fun buildsTextStreamCharCommandWithNoAckResponseMode() {
        val json = JSONObject(
            PcProtocol.keyboardTextStreamChar(
                id = "stream-char-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                streamId = "android-123",
                seq = 2,
                text = "H"
            )
        )

        assertEquals("keyboard.textStream.char", json.getString("type"))
        assertEquals("none", json.getString("responseMode"))
        assertEquals("android-123", json.getJSONObject("payload").getString("streamId"))
        assertEquals(2, json.getJSONObject("payload").getInt("seq"))
        assertEquals("H", json.getJSONObject("payload").getString("text"))
        assertEquals(
            PcProtocol.authProof(
                id = "stream-char-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.textStream.char",
                payload = JSONObject().put("streamId", "android-123").put("seq", 2).put("text", "H"),
                token = "shared-token",
                responseMode = PcCommandResponseMode.None
            ),
            json.getString("auth")
        )
    }

    @Test
    fun buildsTextStreamChunkCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.keyboardTextStreamChunk(
                id = "stream-chunk-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                streamId = "android-123",
                seq = 2,
                text = "Hello"
            )
        )

        assertEquals("keyboard.textStream.chunk", json.getString("type"))
        assertFalse(json.has("responseMode"))
        assertEquals("android-123", json.getJSONObject("payload").getString("streamId"))
        assertEquals(2, json.getJSONObject("payload").getInt("seq"))
        assertEquals("Hello", json.getJSONObject("payload").getString("text"))
        assertEquals(
            PcProtocol.authProof(
                id = "stream-chunk-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.textStream.chunk",
                payload = JSONObject().put("streamId", "android-123").put("seq", 2).put("text", "Hello"),
                token = "shared-token"
            ),
            json.getString("auth")
        )
    }

    @Test
    fun buildsTextStreamKeyCommandWithNoAckResponseMode() {
        val json = JSONObject(
            PcProtocol.keyboardTextStreamKey(
                id = "stream-key-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                streamId = "android-123",
                seq = 3,
                key = PcKeyboardKey.Enter
            )
        )

        assertEquals("keyboard.textStream.key", json.getString("type"))
        assertEquals("none", json.getString("responseMode"))
        assertEquals("android-123", json.getJSONObject("payload").getString("streamId"))
        assertEquals(3, json.getJSONObject("payload").getInt("seq"))
        assertEquals("Enter", json.getJSONObject("payload").getString("key"))
    }

    @Test
    fun buildsTextStreamCloseCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.keyboardTextStreamClose(
                id = "stream-close-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                streamId = "android-123",
                expectedCount = 4
            )
        )

        assertEquals("keyboard.textStream.close", json.getString("type"))
        assertFalse(json.has("responseMode"))
        assertEquals("android-123", json.getJSONObject("payload").getString("streamId"))
        assertEquals(4, json.getJSONObject("payload").getInt("expectedCount"))
        assertEquals(
            PcProtocol.authProof(
                id = "stream-close-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "keyboard.textStream.close",
                payload = JSONObject().put("streamId", "android-123").put("expectedCount", 4),
                token = "shared-token"
            ),
            json.getString("auth")
        )
    }

    @Test
    fun buildsFunctionKeyboardKeyCommand() {
        val json = JSONObject(PcProtocol.keyboardKey("key-f12", "device-1", "token", 1000L, PcKeyboardKey.F12))

        assertEquals("keyboard.key", json.getString("type"))
        assertEquals("F12", json.getJSONObject("payload").getString("key"))
    }

    @Test
    fun buildsWindowControlCommandWithAuthProof() {
        val json = JSONObject(
            PcProtocol.windowControl(
                id = "window-1",
                deviceId = "device-1",
                token = "shared-token",
                timestamp = 1000L,
                action = PcWindowControlAction.SwitchNext
            )
        )

        assertEquals(1, json.getInt("version"))
        assertEquals("window-1", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals(1000L, json.getLong("timestamp"))
        assertEquals("window.control", json.getString("type"))
        assertEquals("switchNext", json.getJSONObject("payload").getString("action"))
        assertEquals(
            PcProtocol.authProof(
                id = "window-1",
                deviceId = "device-1",
                timestamp = 1000L,
                type = "window.control",
                payload = JSONObject().put("action", "switchNext"),
                token = "shared-token"
            ),
            json.getString("auth")
        )
        assertFalse(json.toString().contains("shared-token"))
    }

    @Test
    fun buildsBackspaceKeyboardKeyCommand() {
        val json = JSONObject(PcProtocol.keyboardKey("key-2", "device-1", "token", 1000L, PcKeyboardKey.Backspace))

        assertEquals("keyboard.key", json.getString("type"))
        assertEquals("Backspace", json.getJSONObject("payload").getString("key"))
    }

    private fun validPointerProfileResponse(
        displayId: String = "0:0:1280:720:1.5",
        scaleFactor: Double = 1.5,
        width: Int = 1280,
        capabilities: String = ""
    ): String {
        return """
            {"version":1,"id":"profile-1","type":"pointer.profile","ok":true,"payload":{"displayId":"$displayId","scaleFactor":$scaleFactor,"bounds":{"x":0,"y":0,"width":$width,"height":720},"maxDelta":500,"recommendedDeltas":{"small":50,"medium":130,"large":252}$capabilities},"error":null}
        """.trimIndent()
    }
}

private fun JSONArray.asStringList(): List<String> {
    return (0 until length()).map { index -> getString(index) }
}
