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
    private var repeatedCommand: PcControlCommand? = null
    private var repeatArmed = false

    companion object {
        val instance: PcMouseRepeatManager by lazy { PcMouseRepeatManager() }

        fun isRepeatable(command: PcControlCommand): Boolean {
            return command is PcControlCommand.Move || command is PcControlCommand.Scroll
        }
    }

    fun init(context: Context) {
        settings = PreferencePcMouseRepeatSettings(context.applicationContext)
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

    fun startAfterInitialSend(
        command: PcControlCommand,
        scope: CoroutineScope,
        sendRepeatedCommand: suspend (PcControlCommand) -> PcCommandResult
    ): Boolean {
        if (!isRepeatable(command)) return false
        if (!currentSettings().isEnabled()) {
            stop()
            return false
        }
        if (!repeatArmed || repeatedCommand != command) return false

        repeatJob?.cancel()
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
                if (!sendAndContinue(command, sendRepeatedCommand)) return@launch
            }
        }
        return true
    }

    fun cancelPendingStart(showMessage: Boolean = false): Boolean {
        if (!isRepeating()) return false

        repeatJob?.cancel()
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
        clearRepeatState()
        if (showMessage) {
            showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
        }
        return true
    }

    fun stopForSwitchPress(): Boolean = stop()

    fun isRepeating(): Boolean {
        return repeatArmed && repeatedCommand != null
    }

    fun clearServiceState(showMessage: Boolean = false) {
        stop(showMessage)
    }

    private suspend fun sendAndContinue(
        command: PcControlCommand,
        sendCommand: suspend (PcControlCommand) -> PcCommandResult
    ): Boolean {
        return when (sendCommand(command)) {
            PcCommandResult.Ack -> true
            is PcCommandResult.AuthFailed,
            is PcCommandResult.Failed -> {
                clearRepeatState()
                showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
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
        repeatedCommand = null
        repeatArmed = false
    }

    private fun showMessage(messageResId: Int, severity: MessageSeverity) {
        showHudMessage(messageResId, severity)
    }

    internal fun resetForTesting() {
        repeatJob?.cancel()
        repeatJob = null
        repeatedCommand = null
        repeatArmed = false
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

private fun defaultHudMessageHandler(): (Int, MessageSeverity) -> Unit = { messageResId, severity ->
    ServiceMessageHUD.instance.showMessage(
        messageResId,
        ServiceMessageHUD.MessageType.DISAPPEARING,
        severity = severity
    )
}
