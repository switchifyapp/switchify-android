package com.enaboapps.switchify.pc

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PcMouseRepeatManager private constructor() {
    private var context: Context? = null
    private var repeatJob: Job? = null
    private var repeatedCommand: PcControlCommand? = null
    private var enabledProviderForTesting: (() -> Boolean)? = null
    private var intervalProviderForTesting: (() -> Long)? = null
    private var suppressHudForTesting = false
    private var messageRecorderForTesting: ((Int) -> Unit)? = null

    companion object {
        const val DEFAULT_REPEAT_INTERVAL = 250L
        const val MIN_REPEAT_INTERVAL = 100L
        const val MAX_REPEAT_INTERVAL = 2000L
        const val REPEAT_INTERVAL_STEP = 50L

        val instance: PcMouseRepeatManager by lazy { PcMouseRepeatManager() }

        fun isRepeatable(command: PcControlCommand): Boolean {
            return command is PcControlCommand.Move || command is PcControlCommand.Scroll
        }
    }

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun start(
        command: PcControlCommand,
        scope: CoroutineScope,
        sendCommand: suspend (PcControlCommand) -> PcCommandResult
    ): Boolean {
        if (!isRepeatable(command) || !isEnabled()) return false

        stop(showMessage = false)
        repeatedCommand = command
        repeatJob = scope.launch {
            when (sendCommand(command)) {
                PcCommandResult.Ack -> showMessage(R.string.pc_mouse_repeat_started, MessageSeverity.Success)
                is PcCommandResult.AuthFailed,
                is PcCommandResult.Failed -> {
                    clearRepeatState()
                    showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
                    return@launch
                }
            }

            while (isActive) {
                delay(getRepeatInterval())
                if (!isActive) return@launch
                when (sendCommand(command)) {
                    PcCommandResult.Ack -> Unit
                    is PcCommandResult.AuthFailed,
                    is PcCommandResult.Failed -> {
                        clearRepeatState()
                        showMessage(R.string.pc_mouse_repeat_stopped, MessageSeverity.Info)
                        return@launch
                    }
                }
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

    private fun isEnabled(): Boolean {
        enabledProviderForTesting?.let { return it() }
        return context?.let {
            PreferenceManager(it).getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT,
                true
            )
        } ?: true
    }

    private fun getRepeatInterval(): Long {
        val interval = intervalProviderForTesting?.invoke()
            ?: context?.let {
                PreferenceManager(it).getLongValue(
                    PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT_INTERVAL,
                    DEFAULT_REPEAT_INTERVAL
                )
            }
            ?: DEFAULT_REPEAT_INTERVAL
        return interval.coerceIn(MIN_REPEAT_INTERVAL, MAX_REPEAT_INTERVAL)
    }

    private fun clearRepeatState() {
        repeatJob = null
        repeatedCommand = null
    }

    private fun showMessage(messageResId: Int, severity: MessageSeverity) {
        messageRecorderForTesting?.invoke(messageResId)
        if (suppressHudForTesting) return
        ServiceMessageHUD.instance.showMessage(
            messageResId,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            severity = severity
        )
    }

    internal fun setEnabledProviderForTesting(provider: (() -> Boolean)?) {
        enabledProviderForTesting = provider
    }

    internal fun setIntervalProviderForTesting(provider: (() -> Long)?) {
        intervalProviderForTesting = provider
    }

    internal fun setSuppressHudForTesting(suppress: Boolean) {
        suppressHudForTesting = suppress
    }

    internal fun setMessageRecorderForTesting(recorder: ((Int) -> Unit)?) {
        messageRecorderForTesting = recorder
    }

    internal fun resetForTesting() {
        repeatJob?.cancel()
        repeatJob = null
        repeatedCommand = null
        context = null
        enabledProviderForTesting = null
        intervalProviderForTesting = null
        suppressHudForTesting = false
        messageRecorderForTesting = null
    }
}
