package com.enaboapps.switchify.service.gestures

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GestureRepeatManager private constructor() {
    private var context: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var repeatJob: Job? = null
    private var repeatedGestureData: GestureData? = null
    private var autoRepeatEnabled = false
    private var repeatDelayProviderForTesting: (() -> Long)? = null
    private var suppressHudForTesting = false
    private var messageRecorderForTesting: ((Int) -> Unit)? = null
    private var isExecutingRepeatGesture = false

    companion object {
        const val DEFAULT_REPEAT_DELAY = 1000L
        const val MIN_REPEAT_DELAY = 250L
        const val MAX_REPEAT_DELAY = 10000L
        const val REPEAT_DELAY_STEP = 250L

        val instance: GestureRepeatManager by lazy { GestureRepeatManager() }
    }

    fun init(context: Context) {
        this.context = context
        clearServiceState(showMessage = false)
    }

    fun toggleAutoRepeat(context: Context, syncGestureLock: Boolean = false) {
        val nextEnabled = !isAutoRepeatEnabled(context)
        setAutoRepeatEnabled(context, nextEnabled, syncGestureLock)
    }

    fun setAutoRepeatEnabled(
        context: Context,
        enabled: Boolean,
        syncGestureLock: Boolean = false
    ) {
        val result = GestureModePolicy.setRepeatEnabled(
            enabled,
            currentRepeatEnabled = autoRepeatEnabled,
            currentRearmEnabled = GestureLockManager.instance.isAutoReenableEnabled(),
            isGestureLockEnabled = GestureLockManager.instance.isLocked()
        )
        result.blockedReasonResId?.let {
            showMessage(it, MessageSeverity.Warning)
            return
        }
        applyAutoRepeatToggleResult(result.state.repeatEnabled)
    }

    fun isAutoRepeatEnabled(context: Context): Boolean {
        return isAutoRepeatEnabled()
    }

    fun isAutoRepeatEnabled(): Boolean {
        return autoRepeatEnabled
    }

    fun onGesturePerformed(gestureData: GestureData) {
        if (!isAutoRepeatEnabled() || isRepeating() || isExecutingRepeatGesture) return
        startRepeat(gestureData)
    }

    fun stopRepeat(showMessage: Boolean = true): Boolean {
        if (!isRepeating()) return false
        repeatJob?.cancel()
        repeatJob = null
        repeatedGestureData = null
        if (showMessage) {
            showMessage(R.string.gesture_repeat_stopped, MessageSeverity.Info)
        }
        return true
    }

    fun stopRepeatForSwitchPress(): Boolean {
        val stopped = stopRepeat()
        if (stopped) {
            repeatedGestureData = null
            if (isAutoRepeatEnabled()) {
                showMessage(R.string.gesture_repeat_waiting, MessageSeverity.Info)
            }
        }
        return stopped
    }

    fun isRepeating(): Boolean = repeatJob?.isActive == true

    fun isWaitingForGesture(): Boolean = isAutoRepeatEnabled() && !isRepeating()

    fun clearServiceState(showMessage: Boolean = false) {
        val wasEnabled = autoRepeatEnabled
        stopRepeat(showMessage = false)
        repeatedGestureData = null
        autoRepeatEnabled = false
        if (showMessage && wasEnabled) {
            showMessage(R.string.gesture_repeat_disabled, MessageSeverity.Info)
        }
    }

    private fun applyAutoRepeatToggleResult(nextEnabled: Boolean) {
        autoRepeatEnabled = nextEnabled
        showMessage(
            if (nextEnabled) R.string.gesture_repeat_enabled
            else R.string.gesture_repeat_disabled,
            MessageSeverity.Info
        )
        if (nextEnabled) {
            repeatedGestureData = null
            showMessage(R.string.gesture_repeat_waiting, MessageSeverity.Info)
        } else {
            stopRepeat(showMessage = false)
            repeatedGestureData = null
        }
    }

    private fun startRepeat(gestureData: GestureData) {
        repeatJob?.cancel()
        repeatedGestureData = gestureData
        showMessage(R.string.gesture_repeat_started, MessageSeverity.Success)
        repeatJob = scope.launch {
            while (isActive) {
                delay(getRepeatDelay())
                if (isActive) {
                    isExecutingRepeatGesture = true
                    try {
                        repeatedGestureData?.executeGesture()
                    } finally {
                        isExecutingRepeatGesture = false
                    }
                }
            }
        }
    }

    internal fun turnAutoRepeatOffForGestureLockToggle() {
        clearServiceState(showMessage = false)
    }

    private fun getRepeatDelay(): Long {
        val delay = repeatDelayProviderForTesting?.invoke()
            ?: context?.let {
                PreferenceManager(it).getLongValue(
                    PreferenceManager.PREFERENCE_KEY_GESTURE_REPEAT_DELAY,
                    DEFAULT_REPEAT_DELAY
                )
            }
            ?: DEFAULT_REPEAT_DELAY
        return delay.coerceIn(MIN_REPEAT_DELAY, MAX_REPEAT_DELAY)
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

    internal fun toggleAutoRepeatForTesting(syncGestureLock: Boolean) {
        val nextEnabled = !isAutoRepeatEnabled()
        val result = GestureModePolicy.setRepeatEnabled(
            nextEnabled,
            currentRepeatEnabled = autoRepeatEnabled,
            currentRearmEnabled = GestureLockManager.instance.isAutoReenableEnabled(),
            isGestureLockEnabled = GestureLockManager.instance.isLocked()
        )
        result.blockedReasonResId?.let {
            showMessage(it, MessageSeverity.Warning)
            return
        }
        applyAutoRepeatToggleResult(result.state.repeatEnabled)
    }

    internal fun setAutoRepeatEnabledForTesting(enabled: Boolean) {
        autoRepeatEnabled = enabled
    }

    internal fun setRepeatDelayProviderForTesting(provider: (() -> Long)?) {
        repeatDelayProviderForTesting = provider
    }

    internal fun getRepeatDelayForTesting(): Long {
        return getRepeatDelay()
    }

    internal fun getRepeatedGestureDataForTesting(): GestureData? {
        return repeatedGestureData
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
        repeatedGestureData = null
        context = null
        autoRepeatEnabled = false
        repeatDelayProviderForTesting = null
        suppressHudForTesting = false
        messageRecorderForTesting = null
        isExecutingRepeatGesture = false
    }
}
