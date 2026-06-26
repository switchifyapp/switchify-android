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

    companion object {
        val instance: PcMouseRepeatManager by lazy { PcMouseRepeatManager() }

        fun isRepeatable(command: PcControlCommand): Boolean {
            return command is PcControlCommand.Move || command is PcControlCommand.Scroll
        }
    }

    fun init(context: Context) {
        settings = PreferencePcMouseRepeatSettings(context.applicationContext)
    }

    fun start(
        command: PcControlCommand,
        scope: CoroutineScope,
        sendCommand: suspend (PcControlCommand) -> PcCommandResult
    ): Boolean {
        if (!isRepeatable(command) || !currentSettings().isEnabled()) return false

        stop(showMessage = false)
        repeatedCommand = command
        repeatJob = scope.launch {
            if (!sendAndContinue(command, sendCommand, showStartedMessage = true)) return@launch

            while (isActive) {
                delay(currentSettings().intervalMs())
                if (!isActive) return@launch
                if (!currentSettings().isEnabled()) {
                    stop()
                    return@launch
                }
                if (!sendAndContinue(command, sendCommand)) return@launch
            }
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
        return repeatJob?.isActive == true && repeatedCommand != null
    }

    fun clearServiceState(showMessage: Boolean = false) {
        stop(showMessage)
    }

    private suspend fun sendAndContinue(
        command: PcControlCommand,
        sendCommand: suspend (PcControlCommand) -> PcCommandResult,
        showStartedMessage: Boolean = false
    ): Boolean {
        return when (sendCommand(command)) {
            PcCommandResult.Ack -> {
                if (showStartedMessage) showMessage(R.string.pc_mouse_repeat_started, MessageSeverity.Success)
                true
            }
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
    }

    private fun showMessage(messageResId: Int, severity: MessageSeverity) {
        showHudMessage(messageResId, severity)
    }

    internal fun resetForTesting() {
        repeatJob?.cancel()
        repeatJob = null
        repeatedCommand = null
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
