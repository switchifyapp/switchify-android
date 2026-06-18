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
    private var autoRepeatProviderForTesting: (() -> Boolean)? = null
    private var autoRepeatSetterForTesting: ((Boolean) -> Unit)? = null
    private var repeatDelayProviderForTesting: (() -> Long)? = null
    private var suppressHudForTesting = false
    private var messageRecorderForTesting: ((Int) -> Unit)? = null

    companion object {
        const val DEFAULT_REPEAT_DELAY = 1000L
        const val MIN_REPEAT_DELAY = 250L
        const val MAX_REPEAT_DELAY = 10000L
        const val REPEAT_DELAY_STEP = 250L

        val instance: GestureRepeatManager by lazy { GestureRepeatManager() }
    }

    fun init(context: Context) {
        this.context = context
    }

    fun toggleAutoRepeat(context: Context, syncGestureLock: Boolean = false) {
        val nextEnabled = !isAutoRepeatEnabled(context)
        val state = GestureModePolicy.setRepeatEnabled(context, nextEnabled)
        applyAutoRepeatToggleResult(state.repeatEnabled, syncGestureLock)
    }

    fun isAutoRepeatEnabled(context: Context): Boolean {
        autoRepeatProviderForTesting?.let { return GestureModePolicy.normalizeForTesting().repeatEnabled }
        return GestureModePolicy.isRepeatEnabled(context)
    }

    fun onLockedGestureChanged(gestureData: GestureData?) {
        repeatedGestureData = gestureData
        if (gestureData == null) {
            stopRepeat(showMessage = false)
            return
        }
        if (isAutoRepeatEnabled() && !isRepeating()) {
            startRepeat(gestureData)
        }
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
            GestureLockManager.instance.disableLock(
                allowAutoReenable = false,
                showMessage = false
            )
        }
        return stopped
    }

    fun isRepeating(): Boolean = repeatJob?.isActive == true

    private fun applyAutoRepeatToggleResult(nextEnabled: Boolean, syncGestureLock: Boolean) {
        showMessage(
            if (nextEnabled) R.string.gesture_repeat_enabled
            else R.string.gesture_repeat_disabled,
            MessageSeverity.Info
        )
        if (nextEnabled) {
            repeatedGestureData?.let { gestureData ->
                if (!isRepeating()) startRepeat(gestureData)
            }
            if (syncGestureLock && !GestureLockManager.instance.isGestureLockEngaged()) {
                GestureLockManager.instance.enableLockForNextGesture(showMessage = true)
            }
        } else {
            stopRepeat(showMessage = false)
            if (syncGestureLock) {
                GestureLockManager.instance.disableLock(
                    allowAutoReenable = false,
                    showMessage = true
                )
            }
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
                    repeatedGestureData?.executeGesture()
                }
            }
        }
    }

    private fun isAutoRepeatEnabled(): Boolean {
        autoRepeatProviderForTesting?.let { return GestureModePolicy.normalizeForTesting().repeatEnabled }
        return context?.let { isAutoRepeatEnabled(it) } ?: false
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
        val state = GestureModePolicy.setRepeatEnabledForTesting(nextEnabled)
        applyAutoRepeatToggleResult(state.repeatEnabled, syncGestureLock)
    }

    internal fun setAutoRepeatProviderForTesting(provider: (() -> Boolean)?) {
        autoRepeatProviderForTesting = provider
    }

    internal fun setAutoRepeatSetterForTesting(setter: ((Boolean) -> Unit)?) {
        autoRepeatSetterForTesting = setter
    }

    internal fun setRepeatDelayProviderForTesting(provider: (() -> Long)?) {
        repeatDelayProviderForTesting = provider
    }

    internal fun getRepeatDelayForTesting(): Long {
        return getRepeatDelay()
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
        autoRepeatProviderForTesting = null
        autoRepeatSetterForTesting = null
        repeatDelayProviderForTesting = null
        suppressHudForTesting = false
        messageRecorderForTesting = null
    }
}
