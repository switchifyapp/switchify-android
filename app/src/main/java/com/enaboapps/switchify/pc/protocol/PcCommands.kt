package com.enaboapps.switchify.pc.protocol

data class PcCommand(val type: String, val payload: PcJsonObject = emptyMap()) {
    override fun toString(): String = "PcCommand(type=$type)"
}

enum class PcDisplayDirection(val protocolValue: String) {
    Up("up"),
    Down("down"),
    Left("left"),
    Right("right")
}

sealed class PcRepeatCommand {
    data class Move(val dx: Double, val dy: Double) : PcRepeatCommand()
    data class Scroll(val dx: Double, val dy: Double) : PcRepeatCommand()
    data class Key(val key: String) : PcRepeatCommand()
}

object PcCommandTypes {
    const val CONNECTION_PING = "connection.ping"
    const val POINTER_PROFILE = "pointer.profile"
    const val POINTER_SPEED_SET = "pointer.speed.set"
    const val POINTER_DISPLAY_MOVE = "pointer.display.move"
    const val MOUSE_MOVE = "mouse.move"
    const val MOUSE_SCROLL = "mouse.scroll"
    const val MOUSE_REPEAT_START = "mouse.repeat.start"
    const val MOUSE_REPEAT_STOP = "mouse.repeat.stop"
    const val MOUSE_DRAG_START = "mouse.dragStart"
    const val MOUSE_DRAG_END = "mouse.dragEnd"
    const val MOUSE_CLICK = "mouse.click"
    const val MOUSE_DOUBLE_CLICK = "mouse.doubleClick"
    const val MOUSE_RIGHT_CLICK = "mouse.rightClick"
    const val KEYBOARD_TYPE_TEXT = "keyboard.typeText"
    const val KEYBOARD_STREAM_OPEN = "keyboard.textStream.open"
    const val KEYBOARD_STREAM_CHUNK = "keyboard.textStream.chunk"
    const val KEYBOARD_STREAM_KEY = "keyboard.textStream.key"
    const val KEYBOARD_STREAM_CLOSE = "keyboard.textStream.close"
    const val KEYBOARD_KEY = "keyboard.key"
    const val KEYBOARD_SHORTCUT = "keyboard.shortcut"
    const val KEYBOARD_MODIFIER_DOWN = "keyboard.modifierDown"
    const val KEYBOARD_MODIFIER_UP = "keyboard.modifierUp"
    const val WINDOW_CONTROL = "window.control"
    const val SWITCH_PROFILE_LIST = "switch.profile.list"
    const val SWITCH_SESSION_START = "switch.session.start"
    const val SWITCH_EDGE = "switch.edge"
    const val SWITCH_SYNC = "switch.sync"
    const val SWITCH_SESSION_STOP = "switch.session.stop"
    const val GRID_SWITCH_SET = "grid.switch.set"
    const val GRID_SWITCH_SYNC = "grid.switch.sync"
    const val PAIRING_REQUEST = "pairing.request"
}

object PcCommands {
    fun ping(deviceName: String? = null) = PcCommand(
        PcCommandTypes.CONNECTION_PING,
        if (deviceName.isNullOrEmpty()) emptyMap() else linkedMapOf("deviceName" to deviceName)
    )

    fun pointerProfile() = PcCommand(PcCommandTypes.POINTER_PROFILE)

    fun pointerSpeed(scalePercent: Double) =
        PcCommand(PcCommandTypes.POINTER_SPEED_SET, linkedMapOf("scalePercent" to scalePercent))

    fun displayMove(direction: PcDisplayDirection) =
        PcCommand(PcCommandTypes.POINTER_DISPLAY_MOVE, linkedMapOf("direction" to direction.protocolValue))

    fun move(dx: Double, dy: Double) = PcCommand(PcCommandTypes.MOUSE_MOVE, linkedMapOf("dx" to dx, "dy" to dy))

    fun scroll(dx: Double, dy: Double) = PcCommand(PcCommandTypes.MOUSE_SCROLL, linkedMapOf("dx" to dx, "dy" to dy))

    fun repeatStart(command: PcRepeatCommand) = PcCommand(
        PcCommandTypes.MOUSE_REPEAT_START,
        linkedMapOf(
            "command" to when (command) {
                is PcRepeatCommand.Move -> linkedMapOf(
                    "type" to PcCommandTypes.MOUSE_MOVE,
                    "payload" to linkedMapOf("dx" to command.dx, "dy" to command.dy)
                )
                is PcRepeatCommand.Scroll -> linkedMapOf(
                    "type" to PcCommandTypes.MOUSE_SCROLL,
                    "payload" to linkedMapOf("dx" to command.dx, "dy" to command.dy)
                )
                is PcRepeatCommand.Key -> linkedMapOf(
                    "type" to PcCommandTypes.KEYBOARD_KEY,
                    "payload" to linkedMapOf("key" to command.key)
                )
            }
        )
    )

    fun repeatStop() = PcCommand(PcCommandTypes.MOUSE_REPEAT_STOP)

    fun dragStart(button: String = LEFT_BUTTON) =
        PcCommand(PcCommandTypes.MOUSE_DRAG_START, linkedMapOf("button" to button))

    fun dragEnd(button: String = LEFT_BUTTON) = PcCommand(PcCommandTypes.MOUSE_DRAG_END, linkedMapOf("button" to button))

    fun click(button: String = LEFT_BUTTON) = PcCommand(PcCommandTypes.MOUSE_CLICK, linkedMapOf("button" to button))

    fun doubleClick(button: String = LEFT_BUTTON) =
        PcCommand(PcCommandTypes.MOUSE_DOUBLE_CLICK, linkedMapOf("button" to button))

    fun rightClick() = PcCommand(PcCommandTypes.MOUSE_RIGHT_CLICK)

    fun typeText(text: String) = PcCommand(PcCommandTypes.KEYBOARD_TYPE_TEXT, linkedMapOf("text" to text))

    fun streamOpen(streamId: String) = PcCommand(PcCommandTypes.KEYBOARD_STREAM_OPEN, linkedMapOf("streamId" to streamId))

    fun streamChunk(streamId: String, seq: Int, text: String) = PcCommand(
        PcCommandTypes.KEYBOARD_STREAM_CHUNK,
        linkedMapOf("streamId" to streamId, "seq" to seq, "text" to text)
    )

    fun streamKey(streamId: String, seq: Int, key: String) = PcCommand(
        PcCommandTypes.KEYBOARD_STREAM_KEY,
        linkedMapOf("streamId" to streamId, "seq" to seq, "key" to key)
    )

    fun streamClose(streamId: String, expectedCount: Int) = PcCommand(
        PcCommandTypes.KEYBOARD_STREAM_CLOSE,
        linkedMapOf("streamId" to streamId, "expectedCount" to expectedCount)
    )

    fun key(key: String) = PcCommand(PcCommandTypes.KEYBOARD_KEY, linkedMapOf("key" to key))

    fun shortcut(keys: List<String>) = PcCommand(PcCommandTypes.KEYBOARD_SHORTCUT, linkedMapOf("keys" to keys))

    fun modifierDown(key: String) = PcCommand(PcCommandTypes.KEYBOARD_MODIFIER_DOWN, linkedMapOf("key" to key))

    fun modifierUp(key: String) = PcCommand(PcCommandTypes.KEYBOARD_MODIFIER_UP, linkedMapOf("key" to key))

    fun windowControl(action: String) = PcCommand(PcCommandTypes.WINDOW_CONTROL, linkedMapOf("action" to action))

    fun switchProfileList(includeScanning: Boolean) = PcCommand(
        PcCommandTypes.SWITCH_PROFILE_LIST,
        if (includeScanning) linkedMapOf("includeScanning" to true) else emptyMap()
    )

    fun switchSessionStart(sessionId: String, profileId: String, profileVersion: Int, switchCount: Int) = PcCommand(
        PcCommandTypes.SWITCH_SESSION_START,
        linkedMapOf(
            "sessionId" to sessionId,
            "profileId" to profileId,
            "profileVersion" to profileVersion,
            "switchCount" to switchCount
        )
    )

    fun switchEdge(switchId: Int, down: Boolean, sessionId: String, sequence: Long) = PcCommand(
        PcCommandTypes.SWITCH_EDGE,
        linkedMapOf("switchId" to switchId, "state" to switchState(down), "sessionId" to sessionId, "sequence" to sequence)
    )

    fun switchSync(sessionId: String, sequence: Long, pressedSwitchIds: List<Int>) = PcCommand(
        PcCommandTypes.SWITCH_SYNC,
        linkedMapOf("sessionId" to sessionId, "sequence" to sequence, "pressedSwitchIds" to pressedSwitchIds)
    )

    fun switchSessionStop(sessionId: String, sequence: Long) = PcCommand(
        PcCommandTypes.SWITCH_SESSION_STOP,
        linkedMapOf("sessionId" to sessionId, "sequence" to sequence)
    )

    fun gridSwitchSet(switchId: Int, down: Boolean, sessionId: String? = null, sequence: Long? = null): PcCommand {
        val payload = linkedMapOf<String, Any?>("switchId" to switchId, "state" to switchState(down))
        if (sessionId != null && sequence != null) {
            payload["sessionId"] = sessionId
            payload["sequence"] = sequence
        }
        return PcCommand(PcCommandTypes.GRID_SWITCH_SET, payload)
    }

    fun gridSwitchSync(sessionId: String, sequence: Long, pressedSwitchIds: List<Int>) = PcCommand(
        PcCommandTypes.GRID_SWITCH_SYNC,
        linkedMapOf("sessionId" to sessionId, "sequence" to sequence, "pressedSwitchIds" to pressedSwitchIds)
    )

    private fun switchState(down: Boolean) = if (down) "down" else "up"

    private const val LEFT_BUTTON = "left"
}

object PcMessages {
    fun pairingRequest(
        id: String,
        deviceId: String,
        deviceName: String,
        desktopId: String,
        requestNonce: String
    ): String = PcJson.stringify(
        linkedMapOf(
            "version" to PcProtocolConstants.PROTOCOL_VERSION,
            "id" to id,
            "type" to PcCommandTypes.PAIRING_REQUEST,
            "payload" to linkedMapOf(
                "deviceId" to deviceId,
                "deviceName" to deviceName,
                "desktopId" to desktopId,
                "requestNonce" to requestNonce
            )
        )
    )

    fun authenticatedCommand(
        id: String,
        deviceId: String,
        token: String,
        timestamp: Long,
        command: PcCommand,
        responseMode: PcResponseMode = PcResponseMode.Ack
    ): String {
        val message = linkedMapOf<String, Any?>(
            "version" to PcProtocolConstants.PROTOCOL_VERSION,
            "id" to id,
            "deviceId" to deviceId,
            "timestamp" to timestamp,
            "type" to command.type,
            "payload" to command.payload
        )
        if (responseMode != PcResponseMode.Ack) message["responseMode"] = responseMode.protocolValue
        message["auth"] = PcCanonical.authProof(id, deviceId, timestamp, command.type, command.payload, token, responseMode)
        return PcJson.stringify(message)
    }
}
