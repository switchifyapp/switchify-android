package com.enaboapps.switchify.pc

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PcMouseRepeatManager internal constructor(
    private var settings: PcMouseRepeatSettings? = null,
    private var showHudMessage: (Int, MessageSeverity) -> Unit = defaultHudMessageHandler()
) {
    private var repeatJob: Job? = null
    private var reconnectGraceJob: Job? = null
    private var repeatedCommand: PcControlCommand? = null
    private var repeatArmed = false
    private var pausedForReconnect = false
    private var pcSideRepeatActive = false
    private var pcSideRepeatPending = false
    private var stopPcSideRepeat: (suspend () -> PcCommandResult)? = null
    private var pcSideScope: CoroutineScope? = null

    companion object {
        internal const val RECONNECT_GRACE_MS = 5_000L
        val instance: PcMouseRepeatManager by lazy { PcMouseRepeatManager() }

        fun isRepeatable(command: PcControlCommand): Boolean {
            return command is PcControlCommand.Move || command is PcControlCommand.Scroll
        }
    }

    fun init(context: Context) {
        settings = DefaultPcMouseRepeatSettings
    }

    fun canRepeat(command: PcControlCommand): Boolean {
        return isRepeatable(command) && currentSettings().isEnabled()
    }

    fun armForInitialSend(command: PcControlCommand): Boolean {
        if (!canRepeat(command)) return false

        stop(showMessage = false)
        repeatedCommand = command
        repeatArmed = true
        showMessage(R.string.pc_mouse_repeat_started, MessageSeverity.Success)
        return true
    }

    fun armPcSideRepeat(
        command: PcControlCommand,
        scope: CoroutineScope,
        stopRepeatedCommand: suspend () -> PcCommandResult
    ): Boolean {
        if (!isRepeatable(command)) return false

        stop(showMessage = false)
        pcSideRepeatPending = true
        pcSideRepeatActive = false
        pcSideScope = scope
        stopPcSideRepeat = stopRepeatedCommand
        repeatedCommand = command
        repeatArmed = true
        showMessage(R.string.pc_mouse_repeat_started, MessageSeverity.Success)
        return true
    }

    fun confirmPcSideStarted(command: PcControlCommand): Boolean {
        if (!pcSideRepeatPending || repeatedCommand != command) return false

        pcSideRepeatPending = false
        pcSideRepeatActive = true
        repeatArmed = true
        return true
    }

    fun cancelPcSidePending(showMessage: Boolean = false): Boolean {
        if (!pcSideRepeatPending && !pcSideRepeatActive) return false

        clearPcSideRepeatState()
        if (showMessage) {
            showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
        }
        return true
    }

    fun startAfterInitialSend(
        command: PcControlCommand,
        scope: CoroutineScope,
        shouldPauseForReconnect: () -> Boolean = { false },
        sendRepeatedCommand: suspend (PcControlCommand) -> PcCommandResult
    ): Boolean {
        if (!isRepeatable(command)) return false
        if (!currentSettings().isEnabled()) {
            stop()
            return false
        }
        if (!repeatArmed || repeatedCommand != command) return false

        repeatJob?.cancel()
        reconnectGraceJob?.cancel()
        reconnectGraceJob = null
        pausedForReconnect = false
        startRepeatJob(command, scope, sendRepeatedCommand, shouldPauseForReconnect)
        return true
    }

    fun pauseForReconnect(
        scope: CoroutineScope,
        graceMs: Long = RECONNECT_GRACE_MS
    ): Boolean {
        if (!isRepeating()) return false
        if (pausedForReconnect) return true

        repeatJob?.cancel()
        repeatJob = null
        pausedForReconnect = true
        reconnectGraceJob?.cancel()
        reconnectGraceJob = scope.launch {
            delay(graceMs)
            if (pausedForReconnect) {
                stop(showMessage = true)
            }
        }
        return true
    }

    fun resumeAfterReconnect(
        scope: CoroutineScope,
        sendRepeatedCommand: suspend (PcControlCommand) -> PcCommandResult
    ): Boolean {
        val command = repeatedCommand ?: return false
        if (!pausedForReconnect) return false
        if (!canRepeat(command)) {
            stop(showMessage = false)
            return false
        }

        reconnectGraceJob?.cancel()
        reconnectGraceJob = null
        pausedForReconnect = false
        startRepeatJob(command, scope, sendRepeatedCommand) { false }
        return true
    }

    fun isPausedForReconnect(): Boolean {
        return pausedForReconnect
    }

    private fun startRepeatJob(
        command: PcControlCommand,
        scope: CoroutineScope,
        sendRepeatedCommand: suspend (PcControlCommand) -> PcCommandResult,
        shouldPauseForReconnect: () -> Boolean
    ) {
        repeatedCommand = command
        repeatArmed = true
        repeatJob = scope.launch {
            while (isActive) {
                delay(currentSettings().intervalMs())
                if (!isActive) return@launch
                if (!currentSettings().isEnabled()) {
                    stop()
                    return@launch
                }
                if (!sendAndContinue(command, scope, sendRepeatedCommand, shouldPauseForReconnect)) {
                    return@launch
                }
            }
        }
    }

    fun cancelPendingStart(showMessage: Boolean = false): Boolean {
        if (!isRepeating()) return false

        repeatJob?.cancel()
        reconnectGraceJob?.cancel()
        clearRepeatState()
        if (showMessage) {
            showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
        }
        return true
    }

    fun stop(showMessage: Boolean = true): Boolean {
        if (!isRepeating()) {
            clearRepeatState()
            return false
        }
        repeatJob?.cancel()
        reconnectGraceJob?.cancel()
        clearRepeatState()
        if (showMessage) {
            showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
        }
        return true
    }

    fun stopForSwitchPress(): Boolean {
        if (stopPcSideForSwitchPress()) return true
        return stop()
    }

    private fun stopPcSideForSwitchPress(): Boolean {
        if (!pcSideRepeatPending && !pcSideRepeatActive) return false

        val stop = stopPcSideRepeat
        val scope = pcSideScope
        clearPcSideRepeatState()
        showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
        if (stop != null && scope != null) {
            scope.launch {
                stop()
            }
        }
        return true
    }

    fun isRepeating(): Boolean {
        return (repeatArmed && repeatedCommand != null) || pcSideRepeatPending || pcSideRepeatActive
    }

    fun clearServiceState(showMessage: Boolean = false) {
        stop(showMessage)
    }

    private suspend fun sendAndContinue(
        command: PcControlCommand,
        scope: CoroutineScope,
        sendCommand: suspend (PcControlCommand) -> PcCommandResult,
        shouldPauseForReconnect: () -> Boolean
    ): Boolean {
        return when (sendCommand(command)) {
            PcCommandResult.Ack -> true
            is PcCommandResult.AuthFailed -> {
                clearRepeatState()
                showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
                false
            }
            is PcCommandResult.Failed -> {
                if (shouldPauseForReconnect()) {
                    pauseForReconnect(scope)
                } else {
                    clearRepeatState()
                    showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
                }
                false
            }
        }
    }

    private fun currentSettings(): PcMouseRepeatSettings {
        return settings ?: object : PcMouseRepeatSettings {
            override fun isEnabled(): Boolean = true
            override fun intervalMs(): Long = PcMouseRepeatDefaults.DEFAULT_INTERVAL_MS
        }
    }

    private fun clearRepeatState() {
        repeatJob = null
        reconnectGraceJob = null
        repeatedCommand = null
        repeatArmed = false
        pausedForReconnect = false
        clearPcSideState()
    }

    private fun clearPcSideRepeatState() {
        repeatedCommand = null
        repeatArmed = false
        clearPcSideState()
    }

    private fun clearPcSideState() {
        pcSideRepeatActive = false
        pcSideRepeatPending = false
        stopPcSideRepeat = null
        pcSideScope = null
    }

    private fun showMessage(messageResId: Int, severity: MessageSeverity) {
        showHudMessage(messageResId, severity)
    }

    internal fun resetForTesting() {
        repeatJob?.cancel()
        reconnectGraceJob?.cancel()
        repeatJob = null
        reconnectGraceJob = null
        repeatedCommand = null
        repeatArmed = false
        pausedForReconnect = false
        clearPcSideState()
        settings = null
        showHudMessage = defaultHudMessageHandler()
    }

    internal fun setSettingsForTesting(settings: PcMouseRepeatSettings) {
        this.settings = settings
    }

    internal fun setHudMessageHandlerForTesting(showHudMessage: (Int, MessageSeverity) -> Unit) {
        this.showHudMessage = showHudMessage
    }
}

private object DefaultPcMouseRepeatSettings : PcMouseRepeatSettings {
    override fun isEnabled(): Boolean = true
    override fun intervalMs(): Long = PcMouseRepeatDefaults.DEFAULT_INTERVAL_MS
}

private fun defaultHudMessageHandler(): (Int, MessageSeverity) -> Unit = { messageResId, severity ->
    ServiceMessageHUD.instance.showMessage(
        messageResId,
        ServiceMessageHUD.MessageType.DISAPPEARING,
        severity = severity
    )
}
