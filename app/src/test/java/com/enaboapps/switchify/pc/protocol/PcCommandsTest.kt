package com.enaboapps.switchify.pc.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PcCommandsTest {
    @Test
    fun derivesTheSharedPairingVerificationCodes() {
        assertEquals("610717", PcVerificationCode.derive("desktop-1", "android-1", "nonce-1"))
        assertEquals("215918", PcVerificationCode.derive("desktop-1", "device-1", "nonce-1"))
        assertEquals("735258", PcVerificationCode.derive("0:0:1280:720:1.5", "android-device-id", "random-request-nonce"))
        assertEquals("028314", PcVerificationCode.derive("desktop", "device", "nonce-14"))
    }

    @Test
    fun matchesTheDesktopNestedRepeatPayloadShapes() {
        assertEquals(
            "{\"command\":{\"type\":\"mouse.move\",\"payload\":{\"dx\":64,\"dy\":-64}}}",
            payloadJson(PcCommands.repeatStart(PcRepeatCommand.Move(64.0, -64.0)))
        )
        assertEquals(
            "{\"command\":{\"type\":\"mouse.scroll\",\"payload\":{\"dx\":0,\"dy\":16}}}",
            payloadJson(PcCommands.repeatStart(PcRepeatCommand.Scroll(0.0, 16.0)))
        )
        assertEquals(
            "{\"command\":{\"type\":\"keyboard.key\",\"payload\":{\"key\":\"ArrowDown\"}}}",
            payloadJson(PcCommands.repeatStart(PcRepeatCommand.Key("ArrowDown")))
        )
        assertEquals("mouse.repeat.start", PcCommands.repeatStart(PcRepeatCommand.Key("Tab")).type)
        assertEquals(PcCommand("mouse.repeat.stop", emptyMap()), PcCommands.repeatStop())
    }

    @Test
    fun buildsEveryRemoteCommandPayload() {
        val expected = listOf(
            PcCommands.ping() to ("connection.ping" to "{}"),
            PcCommands.ping("") to ("connection.ping" to "{}"),
            PcCommands.ping("Owen's phone") to ("connection.ping" to "{\"deviceName\":\"Owen's phone\"}"),
            PcCommands.pointerProfile() to ("pointer.profile" to "{}"),
            PcCommands.pointerSpeed(125.0) to ("pointer.speed.set" to "{\"scalePercent\":125}"),
            PcCommands.pointerSpeed(12.5) to ("pointer.speed.set" to "{\"scalePercent\":12.5}"),
            PcCommands.displayMove(PcDisplayDirection.Left) to ("pointer.display.move" to "{\"direction\":\"left\"}"),
            PcCommands.move(10.0, -5.0) to ("mouse.move" to "{\"dx\":10,\"dy\":-5}"),
            PcCommands.scroll(0.0, 3.0) to ("mouse.scroll" to "{\"dx\":0,\"dy\":3}"),
            PcCommands.dragStart() to ("mouse.dragStart" to "{\"button\":\"left\"}"),
            PcCommands.dragEnd("right") to ("mouse.dragEnd" to "{\"button\":\"right\"}"),
            PcCommands.click() to ("mouse.click" to "{\"button\":\"left\"}"),
            PcCommands.doubleClick() to ("mouse.doubleClick" to "{\"button\":\"left\"}"),
            PcCommands.rightClick() to ("mouse.rightClick" to "{}"),
            PcCommands.typeText("Hello") to ("keyboard.typeText" to "{\"text\":\"Hello\"}"),
            PcCommands.streamOpen("s-1") to ("keyboard.textStream.open" to "{\"streamId\":\"s-1\"}"),
            PcCommands.streamChunk("s-1", 2, "hi") to ("keyboard.textStream.chunk" to "{\"streamId\":\"s-1\",\"seq\":2,\"text\":\"hi\"}"),
            PcCommands.streamKey("s-1", 3, "Backspace") to ("keyboard.textStream.key" to "{\"streamId\":\"s-1\",\"seq\":3,\"key\":\"Backspace\"}"),
            PcCommands.streamClose("s-1", 4) to ("keyboard.textStream.close" to "{\"streamId\":\"s-1\",\"expectedCount\":4}"),
            PcCommands.key("Enter") to ("keyboard.key" to "{\"key\":\"Enter\"}"),
            PcCommands.shortcut(listOf("Ctrl", "C")) to ("keyboard.shortcut" to "{\"keys\":[\"Ctrl\",\"C\"]}"),
            PcCommands.modifierDown("Shift") to ("keyboard.modifierDown" to "{\"key\":\"Shift\"}"),
            PcCommands.modifierUp("Shift") to ("keyboard.modifierUp" to "{\"key\":\"Shift\"}"),
            PcCommands.windowControl("minimize") to ("window.control" to "{\"action\":\"minimize\"}"),
            PcCommands.switchProfileList(false) to ("switch.profile.list" to "{}"),
            PcCommands.switchProfileList(true) to ("switch.profile.list" to "{\"includeScanning\":true}"),
            PcCommands.switchSessionStart(SESSION, "keyboard", 2L, 3) to
                ("switch.session.start" to "{\"sessionId\":\"$SESSION\",\"profileId\":\"keyboard\",\"profileVersion\":2,\"switchCount\":3}"),
            PcCommands.switchEdge(1, true, SESSION, 5) to
                ("switch.edge" to "{\"switchId\":1,\"state\":\"down\",\"sessionId\":\"$SESSION\",\"sequence\":5}"),
            PcCommands.switchSync(SESSION, 6, listOf(1, 3)) to
                ("switch.sync" to "{\"sessionId\":\"$SESSION\",\"sequence\":6,\"pressedSwitchIds\":[1,3]}"),
            PcCommands.switchSessionStop(SESSION, 7) to ("switch.session.stop" to "{\"sessionId\":\"$SESSION\",\"sequence\":7}"),
            PcCommands.gridSwitchSet(2, false) to ("grid.switch.set" to "{\"switchId\":2,\"state\":\"up\"}"),
            PcCommands.gridSwitchSet(2, true, SESSION, 8) to
                ("grid.switch.set" to "{\"switchId\":2,\"state\":\"down\",\"sessionId\":\"$SESSION\",\"sequence\":8}"),
            PcCommands.gridSwitchSync(SESSION, 9, emptyList()) to
                ("grid.switch.sync" to "{\"sessionId\":\"$SESSION\",\"sequence\":9,\"pressedSwitchIds\":[]}")
        )
        expected.forEach { (command, shape) ->
            assertEquals(shape.first, command.type)
            assertEquals(shape.first, shape.second, payloadJson(command))
        }
    }

    @Test
    fun acceptsTheDesktopsUnsignedThirtyTwoBitProfileVersions() {
        assertEquals(
            "{\"sessionId\":\"$SESSION\",\"profileId\":\"keyboard\",\"profileVersion\":4294967295,\"switchCount\":1}",
            payloadJson(PcCommands.switchSessionStart(SESSION, "keyboard", 4_294_967_295L, 1))
        )
        assertEquals(true, PcCommands.isValidProfileVersion(1))
        assertEquals(true, PcCommands.isValidProfileVersion(4_294_967_295L))
        assertFalse(PcCommands.isValidProfileVersion(0))
        assertFalse(PcCommands.isValidProfileVersion(-1))
        assertFalse(PcCommands.isValidProfileVersion(4_294_967_296L))
    }

    @Test
    fun serializesThePairingRequestEnvelope() {
        assertEquals(
            "{\"version\":1,\"id\":\"pair-1\",\"type\":\"pairing.request\",\"payload\":{\"deviceId\":\"device-1\"," +
                "\"deviceName\":\"Phone\",\"desktopId\":\"desktop-1\",\"requestNonce\":\"nonce-1\"}}",
            PcMessages.pairingRequest("pair-1", "device-1", "Phone", "desktop-1", "nonce-1")
        )
    }

    @Test
    fun serializesAuthenticatedEnvelopesWithOptionalNoAckMode() {
        val ack = PcMessages.authenticatedCommand("req-1", "device-1", "shared-token", 1000L, PcCommands.ping())
        assertEquals(
            "{\"version\":1,\"id\":\"req-1\",\"deviceId\":\"device-1\",\"timestamp\":1000,\"type\":\"connection.ping\"," +
                "\"payload\":{},\"auth\":\"98bZHKWHa3ooOYZyXBuYpzOdbPWGW5FV04fEjxAl9sI\"}",
            ack
        )
        val none = JSONObject(
            PcMessages.authenticatedCommand("move-1", "device-1", "shared-token", 1000L, PcCommands.move(10.0, -5.0), PcResponseMode.None)
        )
        assertEquals("none", none.getString("responseMode"))
        assertEquals("uMjPQZGMGXXbQuVdbAwz-KG3sHYyiyuTbl8TYx9jl0E", none.getString("auth"))
        assertFalse(none.toString().contains("shared-token"))
    }

    private fun payloadJson(command: PcCommand) = PcJson.stringify(command.payload)

    private companion object {
        const val SESSION = "123e4567-e89b-12d3-a456-426614174000"
    }
}
