package com.enaboapps.switchify.pc.bluetooth

import com.enaboapps.switchify.pc.PcCommandResponseMode
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcProtocol

internal fun PcControlCommand.toProtocolMessage(
    id: String,
    deviceId: String,
    token: String,
    timestamp: Long,
    responseMode: PcCommandResponseMode = PcCommandResponseMode.Ack
): String {
    return when (this) {
        is PcControlCommand.Move -> PcProtocol.mouseMove(id, deviceId, token, timestamp, dx, dy, responseMode)
        is PcControlCommand.Scroll -> PcProtocol.mouseScroll(id, deviceId, token, timestamp, dx, dy, responseMode)
        is PcControlCommand.RepeatStart -> PcProtocol.mouseRepeatStart(id, deviceId, token, timestamp, command)
        PcControlCommand.RepeatStop -> PcProtocol.mouseRepeatStop(id, deviceId, token, timestamp)
        is PcControlCommand.DragStart -> PcProtocol.mouseDragStart(id, deviceId, token, timestamp, button, responseMode)
        is PcControlCommand.DragEnd -> PcProtocol.mouseDragEnd(id, deviceId, token, timestamp, button, responseMode)
        PcControlCommand.LeftClick -> PcProtocol.mouseClick(id, deviceId, token, timestamp, responseMode = responseMode)
        PcControlCommand.DoubleClick -> PcProtocol.mouseDoubleClick(id, deviceId, token, timestamp, responseMode = responseMode)
        PcControlCommand.RightClick -> PcProtocol.mouseRightClick(id, deviceId, token, timestamp, responseMode)
        is PcControlCommand.TypeText -> PcProtocol.keyboardTypeText(id, deviceId, token, timestamp, text, responseMode)
        is PcControlCommand.TextStreamOpen -> PcProtocol.keyboardTextStreamOpen(id, deviceId, token, timestamp, streamId, responseMode)
        is PcControlCommand.TextStreamChar -> PcProtocol.keyboardTextStreamChar(id, deviceId, token, timestamp, streamId, seq, text, responseMode)
        is PcControlCommand.TextStreamChunk -> PcProtocol.keyboardTextStreamChunk(id, deviceId, token, timestamp, streamId, seq, text, responseMode)
        is PcControlCommand.TextStreamKey -> PcProtocol.keyboardTextStreamKey(id, deviceId, token, timestamp, streamId, seq, key, responseMode)
        is PcControlCommand.TextStreamClose -> PcProtocol.keyboardTextStreamClose(id, deviceId, token, timestamp, streamId, expectedCount, responseMode)
        is PcControlCommand.PressKey -> PcProtocol.keyboardKey(id, deviceId, token, timestamp, key, responseMode)
        is PcControlCommand.KeyboardShortcut -> PcProtocol.keyboardShortcut(id, deviceId, token, timestamp, keys, responseMode)
        is PcControlCommand.ModifierDown -> PcProtocol.keyboardModifierDown(id, deviceId, token, timestamp, key, responseMode)
        is PcControlCommand.ModifierUp -> PcProtocol.keyboardModifierUp(id, deviceId, token, timestamp, key, responseMode)
        is PcControlCommand.WindowControl -> PcProtocol.windowControl(id, deviceId, token, timestamp, action, responseMode)
        is PcControlCommand.SetPointerSpeed -> PcProtocol.pointerSpeedSet(id, deviceId, token, timestamp, scalePercent)
        is PcControlCommand.MoveToDisplay -> PcProtocol.pointerDisplayMove(id, deviceId, token, timestamp, direction)
        is PcControlCommand.LegacyGridSwitchSet -> PcProtocol.legacyGridSwitchSet(
            id, deviceId, token, timestamp, switchId, down, sessionId, sequence, responseMode
        )
        is PcControlCommand.LegacyGridSwitchSync -> PcProtocol.legacyGridSwitchSync(
            id, deviceId, token, timestamp, sessionId, sequence, pressedSwitchIds
        )
        PcControlCommand.SwitchProfileList -> PcProtocol.switchProfileList(id, deviceId, token, timestamp)
        is PcControlCommand.SwitchSessionStart -> PcProtocol.switchSessionStart(
            id, deviceId, token, timestamp, sessionId, profileId, profileVersion, switchCount
        )
        is PcControlCommand.SwitchEdge -> PcProtocol.switchEdge(
            id, deviceId, token, timestamp, sessionId, sequence, switchId, down, responseMode
        )
        is PcControlCommand.SwitchSync -> PcProtocol.switchSync(
            id, deviceId, token, timestamp, sessionId, sequence, pressedSwitchIds
        )
        is PcControlCommand.SwitchSessionStop -> PcProtocol.switchSessionStop(
            id, deviceId, token, timestamp, sessionId, sequence
        )
    }
}

internal fun PcPointerMovementProfile.supportsNoAck(command: PcControlCommand): Boolean {
    return capabilities.noAckCommands.contains(command.protocolType()) ||
        (command is PcControlCommand.Move && capabilities.noAckMouseMove)
}

private fun PcControlCommand.protocolType(): String {
    return when (this) {
        is PcControlCommand.Move -> "mouse.move"
        is PcControlCommand.Scroll -> "mouse.scroll"
        is PcControlCommand.RepeatStart -> "mouse.repeat.start"
        PcControlCommand.RepeatStop -> "mouse.repeat.stop"
        is PcControlCommand.DragStart -> "mouse.dragStart"
        is PcControlCommand.DragEnd -> "mouse.dragEnd"
        PcControlCommand.LeftClick -> "mouse.click"
        PcControlCommand.DoubleClick -> "mouse.doubleClick"
        PcControlCommand.RightClick -> "mouse.rightClick"
        is PcControlCommand.TypeText -> "keyboard.typeText"
        is PcControlCommand.TextStreamOpen -> "keyboard.textStream.open"
        is PcControlCommand.TextStreamChar -> "keyboard.textStream.char"
        is PcControlCommand.TextStreamChunk -> "keyboard.textStream.chunk"
        is PcControlCommand.TextStreamKey -> "keyboard.textStream.key"
        is PcControlCommand.TextStreamClose -> "keyboard.textStream.close"
        is PcControlCommand.PressKey -> "keyboard.key"
        is PcControlCommand.KeyboardShortcut -> "keyboard.shortcut"
        is PcControlCommand.ModifierDown -> "keyboard.modifierDown"
        is PcControlCommand.ModifierUp -> "keyboard.modifierUp"
        is PcControlCommand.WindowControl -> "window.control"
        is PcControlCommand.SetPointerSpeed -> "pointer.speed.set"
        is PcControlCommand.MoveToDisplay -> "pointer.display.move"
        is PcControlCommand.LegacyGridSwitchSet -> PcProtocol.LEGACY_GRID_SWITCH_SET_COMMAND
        is PcControlCommand.LegacyGridSwitchSync -> PcProtocol.LEGACY_GRID_SWITCH_SYNC_COMMAND
        PcControlCommand.SwitchProfileList -> PcProtocol.SWITCH_PROFILE_LIST_COMMAND
        is PcControlCommand.SwitchSessionStart -> PcProtocol.SWITCH_SESSION_START_COMMAND
        is PcControlCommand.SwitchEdge -> PcProtocol.SWITCH_EDGE_COMMAND
        is PcControlCommand.SwitchSync -> PcProtocol.SWITCH_SYNC_COMMAND
        is PcControlCommand.SwitchSessionStop -> PcProtocol.SWITCH_SESSION_STOP_COMMAND
    }
}
