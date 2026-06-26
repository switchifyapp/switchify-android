package com.enaboapps.switchify.pc

import kotlinx.coroutines.flow.Flow

sealed class PcPairingResult {
    data class Paired(val desktopId: String, val token: String, val endpointId: String) : PcPairingResult()
    data class Failed(val reason: PcErrorReason, val message: String) : PcPairingResult()
}

sealed class PcPingResult {
    data class Connected(val endpointId: String) : PcPingResult()
    data class AuthFailed(val message: String = "Connection expired. Request access again.") : PcPingResult()
    data class Failed(val reason: PcErrorReason, val message: String) : PcPingResult()
}

sealed class PcControlCommand {
    data class Move(val dx: Int, val dy: Int) : PcControlCommand()
    data class Scroll(val dx: Int, val dy: Int) : PcControlCommand()
    data class DragStart(val button: String = "left") : PcControlCommand()
    data class DragEnd(val button: String = "left") : PcControlCommand()
    data class TypeText(val text: String) : PcControlCommand()
    data class TextStreamOpen(val streamId: String) : PcControlCommand()
    data class TextStreamChar(val streamId: String, val seq: Int, val text: String) : PcControlCommand()
    data class TextStreamChunk(val streamId: String, val seq: Int, val text: String) : PcControlCommand()
    data class TextStreamKey(val streamId: String, val seq: Int, val key: PcKeyboardKey) : PcControlCommand()
    data class TextStreamClose(val streamId: String, val expectedCount: Int) : PcControlCommand()
    data class PressKey(val key: PcKeyboardKey) : PcControlCommand()
    data class KeyboardShortcut(val keys: List<PcKeyboardShortcutKey>) : PcControlCommand()
    data class WindowControl(val action: PcWindowControlAction) : PcControlCommand()
    data object LeftClick : PcControlCommand()
    data object DoubleClick : PcControlCommand()
    data object RightClick : PcControlCommand()
}

sealed class PcCommandResult {
    data object Ack : PcCommandResult()
    data class AuthFailed(val message: String = "Connection expired. Connect to PC from Switchify first.") : PcCommandResult()
    data class Failed(val message: String = "Could not send command to PC.") : PcCommandResult()
}

sealed class PcLiveControlResult {
    data class Connected(val connection: PcControlConnection) : PcLiveControlResult()
    data class AuthFailed(val message: String = "Connection expired. Connect to PC from Switchify first.") : PcLiveControlResult()
    data class Failed(val message: String = "Could not connect to PC.") : PcLiveControlResult()
}

interface PcControlConnection {
    val pointerProfile: PcPointerMovementProfile?
    val connectionEvents: Flow<PcControlConnectionEvent>
    suspend fun checkHealth(): PcCommandResult
    suspend fun sendCommand(command: PcControlCommand): PcCommandResult
    suspend fun sendRealtimeCommand(command: PcControlCommand): PcCommandResult
    fun close(reason: PcControlCloseReason = PcControlCloseReason.ExplicitStop)
}

enum class PcControlCloseReason(val logName: String) {
    UiPauseGraceExpired("ui_pause_grace_expired"),
    ExplicitStop("explicit_stop"),
    CommandFailureRecovery("command_failure_recovery"),
    AuthFailure("auth_failure"),
    ConnectorShutdown("connector_shutdown"),
    Reconnect("reconnect"),
    UnexpectedDisconnect("unexpected_disconnect")
}

sealed class PcControlConnectionEvent {
    data object Disconnected : PcControlConnectionEvent()
}

internal fun resolveExpectedResponse(response: PcProtocolResponse, requestId: String): PcProtocolResponse? {
    return when (response) {
        is PcProtocolResponse.Ack -> response.takeIf { it.id == requestId }
        is PcProtocolResponse.PairingComplete -> response.takeIf { it.id == requestId }
        is PcProtocolResponse.PointerProfile -> response.takeIf { it.id == requestId }
        is PcProtocolResponse.Error -> response.takeIf { it.id == requestId || it.id == null }
        PcProtocolResponse.Invalid -> null
    }
}

interface PcConnector {
    suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult
    suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult
    suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult
    suspend fun sendCommand(session: PcAuthenticatedSession, command: PcControlCommand): PcCommandResult
    fun close()
}
