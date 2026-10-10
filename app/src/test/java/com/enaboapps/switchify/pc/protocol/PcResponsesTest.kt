package com.enaboapps.switchify.pc.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcResponsesTest {
    @Test
    fun parsesDiscoveryStatus() {
        assertEquals(
            PcStatus("pc-1", "Desk", PcPlatform.Windows),
            PcResponses.parseStatus("{\"protocolVersion\":1,\"desktopId\":\"pc-1\",\"displayName\":\"Desk\",\"platform\":\"windows\"}")
        )
        assertEquals(
            PcStatus("pc-2", "Switchify PC", PcPlatform.MacOs),
            PcResponses.parseStatus("{\"protocolVersion\":1,\"desktopId\":\"pc-2\",\"displayName\":\"  \",\"platform\":\"macos\"}")
        )
        assertEquals(null, PcResponses.parseStatus("{\"protocolVersion\":1,\"desktopId\":\"pc\",\"platform\":\"linux\"}")?.platform)
        assertNull(PcResponses.parseStatus("{\"protocolVersion\":2,\"desktopId\":\"pc\"}"))
        assertNull(PcResponses.parseStatus("{\"protocolVersion\":1,\"desktopId\":\"\"}"))
        assertNull(PcResponses.parseStatus("garbage"))
    }

    @Test
    fun negotiatesTheReadResponseTransportExplicitly() {
        val base = "{\"protocolVersion\":1,\"desktopId\":\"pc\",\"platform\":\"linux\""
        assertNull(PcResponses.parseStatus("$base}")!!.responseTransport)
        assertEquals(PcResponseTransport.ReadV1, PcResponses.parseStatus("$base,\"responseTransport\":\"read-v1\"}")!!.responseTransport)
        listOf("\"future\"", "null", "true").forEach {
            assertNull(it, PcResponses.parseStatus("$base,\"responseTransport\":$it}"))
        }
    }

    @Test
    fun parsesAcknowledgementsPairingAndSanitizedErrors() {
        assertEquals(PcResponse.Ack("a"), PcResponses.parseResponse("{\"type\":\"ack\",\"id\":\"a\",\"ok\":true,\"error\":null}"))
        val pairing = PcResponses.parseResponse(
            "{\"type\":\"pairing.complete\",\"id\":\"p\",\"ok\":true,\"error\":null,\"payload\":{\"desktopId\":\"pc-1\",\"deviceId\":\"phone-1\",\"token\":\"secret\"}}"
        )
        assertEquals(PcResponse.PairingComplete("p", "pc-1", "phone-1", "secret"), pairing)
        assertFalse(pairing.toString().contains("secret"))
        assertEquals(
            PcResponse.Error(null, "auth_failed", "invalid_signature"),
            PcResponses.parseResponse("{\"type\":\"error\",\"error\":{\"code\":\"auth_failed\",\"message\":\"invalid_signature\"}}")
        )
        listOf("duplicate_request", "expired_timestamp", "invalid_auth", "name_update_failed").forEach { code ->
            assertEquals(
                PcResponse.Error("x", code, code),
                PcResponses.parseResponse("{\"version\":1,\"id\":\"x\",\"type\":\"error\",\"ok\":false,\"error\":{\"code\":\"$code\",\"message\":\"$code\"}}")
            )
        }
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse("not json"))
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse("{\"type\":\"ack\",\"id\":\"a\",\"ok\":false}"))
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse("{\"type\":\"ack\",\"ok\":true}"))
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse("{\"type\":\"ack\",\"id\":\"a\",\"ok\":true,\"error\":{}}"))
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse("{\"type\":\"error\",\"error\":{\"code\":1,\"message\":\"m\"}}"))
        assertEquals(
            PcResponse.Invalid,
            PcResponses.parseResponse("{\"type\":\"pairing.complete\",\"id\":\"p\",\"ok\":true,\"payload\":{\"desktopId\":\"pc\",\"deviceId\":\"d\"}}")
        )
    }

    @Test
    fun parsesTheDesktopPointerProfileWithCapabilities() {
        val response = PcResponses.parseResponse(DESKTOP_POINTER_PROFILE) as PcResponse.PointerProfile
        val profile = response.profile
        assertEquals("profile-1", response.id)
        assertEquals("display:0:0:1920:1080:1", profile.displayId)
        assertEquals(PcBounds(0.0, 0.0, 1920.0, 1080.0), profile.bounds)
        assertEquals(PcRecommendedDeltas(49.0, 130.0, 281.0), profile.recommendedDeltas)
        assertEquals(500.0, profile.maxDelta, 0.0)
        val capabilities = profile.capabilities
        assertTrue(capabilities.noAckMouseMove)
        assertTrue(capabilities.switchScanning)
        assertTrue(capabilities.supportsNoAck("switch.edge"))
        assertEquals(PcResponseMode.None, capabilities.noAckResponseModeFor("mouse.move"))
        assertEquals(PcResponseMode.Ack, capabilities.noAckResponseModeFor("keyboard.textStream.open"))
        assertTrue(capabilities.supports("pointer.display.move"))
        assertFalse(capabilities.supports("grid.switch.set"))
        assertEquals(PcMouseRepeatCapability(true, true, 250.0, 100.0, 2000.0), capabilities.mouseRepeat)
        assertEquals(
            PcKeyRepeatCapability(true, false, 500.0, 250.0, 100.0, 1000.0, listOf("ArrowUp", "Tab")),
            capabilities.keyRepeat
        )
        assertEquals(PcPointerSpeedCapability(true, true, 150.0, 5.0, 1350.0, 5.0, 64.0, 96.0), capabilities.pointerSpeed)
        assertEquals(PcDisplayNavigationCapability(true, 2.0), capabilities.displayNavigation)
    }

    @Test
    fun treatsADesktopWithoutKeyRepeatAsUnableToRepeatKeys() {
        val json = JSONObject(DESKTOP_POINTER_PROFILE)
        val capabilities = json.getJSONObject("payload").getJSONObject("capabilities")
        capabilities.remove("keyRepeat")
        val older = PcResponses.parseResponse(json.toString()) as PcResponse.PointerProfile
        assertEquals(PcKeyRepeatCapability(false, false, 250.0, 500.0, 100.0, 1000.0, emptyList()), older.profile.capabilities.keyRepeat)
        assertEquals(PcMouseRepeatCapability(true, true, 250.0, 100.0, 2000.0), older.profile.capabilities.mouseRepeat)
    }

    @Test
    fun usesSafeDefaultsWhenCapabilitiesAreMissingOrMalformed() {
        val minimal = "{\"version\":1,\"id\":\"p\",\"type\":\"pointer.profile\",\"ok\":true,\"error\":null,\"payload\":{" +
            "\"displayId\":\"d\",\"scaleFactor\":1.5,\"maxDelta\":500,\"bounds\":{\"x\":0,\"y\":0,\"width\":10,\"height\":10}," +
            "\"recommendedDeltas\":{\"small\":1,\"medium\":2,\"large\":3},\"capabilities\":{\"noAckCommands\":[\"mouse.move\",1]}}}"
        val profile = (PcResponses.parseResponse(minimal) as PcResponse.PointerProfile).profile
        assertEquals(1.5, profile.scaleFactor, 0.0)
        assertEquals(emptyList<String>(), profile.capabilities.noAckCommands)
        assertFalse(profile.capabilities.mouseRepeat.supported)
        assertEquals(PcPointerSpeedCapability(false, false, 100.0, 5.0, 225.0, 5.0, 128.0, 128.0), profile.capabilities.pointerSpeed)
        assertEquals(PcDisplayNavigationCapability(false, 1.0), profile.capabilities.displayNavigation)
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse(minimal.replace("\"maxDelta\":500,", "")))
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse(minimal.replace("\"large\":3", "\"large\":\"3\"")))
    }

    @Test
    fun parsesSwitchProfileCatalogs() {
        val response = PcResponses.parseResponse(
            "{\"type\":\"switch.profile.list\",\"id\":\"profiles\",\"ok\":true,\"error\":null,\"payload\":{\"catalogRevision\":4," +
                "\"profiles\":[{\"id\":\"keyboard\",\"version\":1,\"name\":\"Keyboard\",\"kind\":\"mapped\"," +
                "\"bindings\":[{\"switchId\":1,\"label\":\"Space\",\"behavior\":\"stateful\"}]}]}}"
        )
        assertEquals(
            PcResponse.SwitchProfileCatalog(
                "profiles",
                PcSwitchProfileCatalog(
                    4,
                    listOf(
                        PcSwitchProfile(
                            "keyboard",
                            1,
                            "Keyboard",
                            PcSwitchProfileKind.Mapped,
                            listOf(PcSwitchBinding(1, "Space", PcSwitchBindingBehavior.Stateful))
                        )
                    )
                )
            ),
            response
        )
    }

    @Test
    fun acceptsTheScanningCatalogAndRejectsUnknownKindsOrBindings() {
        val scanning = "{\"type\":\"switch.profile.list\",\"id\":\"scan\",\"ok\":true,\"error\":null,\"payload\":{\"catalogRevision\":1," +
            "\"profiles\":[{\"id\":\"builtin.switchify-scanning\",\"version\":2,\"name\":\"Switchify scanning\",\"kind\":\"scanning\"," +
            "\"bindings\":[{\"switchId\":1,\"label\":\"Select; hold: Next\",\"behavior\":\"stateful\"}]}]}}"
        val catalog = (PcResponses.parseResponse(scanning) as PcResponse.SwitchProfileCatalog).catalog
        assertEquals(PcSwitchProfileKind.Scanning, catalog.profiles.single().kind)
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse(scanning.replace("\"kind\":\"scanning\"", "\"kind\":\"unknown\"")))
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse(scanning.replace("\"switchId\":1", "\"switchId\":9")))
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse(scanning.replace("\"stateful\"", "\"toggle\"")))
        assertEquals(PcResponse.Invalid, PcResponses.parseResponse(scanning.replace("\"catalogRevision\":1", "\"catalogRevision\":-1")))
    }

    private companion object {
        val DESKTOP_POINTER_PROFILE = """
            {"version":1,"id":"profile-1","type":"pointer.profile","ok":true,"payload":{
              "displayId":"display:0:0:1920:1080:1","scaleFactor":1.0,
              "bounds":{"x":0,"y":0,"width":1920,"height":1080},"maxDelta":500,
              "recommendedDeltas":{"small":49,"medium":130,"large":281},
              "capabilities":{"noAckMouseMove":true,"switchScanning":true,
                "noAckCommands":["mouse.move","mouse.click","mouse.repeat.start","mouse.repeat.stop","keyboard.key","switch.edge"],
                "supportedCommands":["mouse.move","connection.ping","pointer.profile","keyboard.textStream.open","switch.edge","pointer.display.move"],
                "mouseRepeat":{"supported":true,"enabled":true,"intervalMs":250,"moveIntervalMs":250,"scrollIntervalMs":300,
                  "minIntervalMs":100,"maxIntervalMs":2000,"accelerationDurationMs":500,
                  "accelerationDurationOptionsMs":[0,500,1000,2000],"accelerationInitialScalePercent":25},
                "keyRepeat":{"supported":true,"enabled":false,"intervalMs":500,"initialDelayMs":250,"minIntervalMs":100,
                  "maxIntervalMs":1000,"repeatableKeys":["ArrowUp","Tab"]},
                "pointerSpeed":{"supported":true,"setSupported":true,"scalePercent":150,"minScalePercent":5,
                  "maxScalePercent":1350,"stepPercent":5,"baseMoveDelta":64,"effectiveMoveDelta":96},
                "displayNavigation":{"supported":true,"displayCount":2}}},
             "error":null}
        """.trimIndent()
    }
}
