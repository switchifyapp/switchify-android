package com.enaboapps.switchify.service.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.core.Tasks
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import java.util.Timer
import java.util.TimerTask

class GestureLockManager private constructor() {
    private var isLocked = false
    private var lockedGestureData: GestureData? = null
    private var timeoutTimer: Timer? = null
    private val lockTimeout = 120000L
    private var accessibilityService: AccessibilityService? = null
    private var autoReenableEnabled = false
    private var suppressHudForTesting = false
    private var messageRecorderForTesting: ((Int) -> Unit)? = null

    companion object {
        val instance: GestureLockManager by lazy { GestureLockManager() }
    }

    fun init(service: AccessibilityService) {
        accessibilityService = service
        clearServiceState(showMessage = false)
    }

    fun toggleAutoReenable(context: Context, syncGestureLock: Boolean = false) {
        val nextEnabled = !isAutoReenableEnabled(context)
        setAutoReenableEnabled(context, nextEnabled, syncGestureLock)
    }

    fun setAutoReenableEnabled(
        context: Context,
        enabled: Boolean,
        syncGestureLock: Boolean = false
    ) {
        val result = GestureModePolicy.setRearmEnabled(
            enabled,
            currentRepeatEnabled = GestureRepeatManager.instance.isAutoRepeatEnabled(),
            currentRearmEnabled = autoReenableEnabled,
            isGestureLockEnabled = isLocked(),
            isAutoScrollActive = AutoScrollManager.getInstance().isAutoScrolling()
        )
        result.blockedReasonResId?.let {
            showMessage(it, MessageSeverity.Warning)
            return
        }
        applyAutoReenableToggleResult(result.state.rearmEnabled, syncGestureLock)
    }

    private fun applyAutoReenableToggleResult(nextEnabled: Boolean, syncGestureLock: Boolean) {
        autoReenableEnabled = nextEnabled
        showMessage(
            if (nextEnabled) R.string.gesture_lock_rearm_enabled
            else R.string.gesture_lock_rearm_disabled,
            MessageSeverity.Info
        )
        if (syncGestureLock) {
            if (nextEnabled) {
                enableLockForNextGesture(showMessage = true)
            } else {
                disableLock(allowAutoReenable = false, showMessage = true)
            }
        }
    }

    // Function to lock/unlock the gesture lock, showing a message to the user
    fun toggleGestureLock() {
        stopTimer()

        if (!isLocked) {
            when {
                isAutoRepeatEnabled() -> {
                    showMessage(R.string.gesture_mode_blocked_repeat_enabled, MessageSeverity.Warning)
                    return
                }
                isAutoReenableEnabled() -> {
                    showMessage(R.string.gesture_mode_blocked_rearm_enabled_for_lock, MessageSeverity.Warning)
                    return
                }
                AutoScrollManager.getInstance().isAutoScrolling() -> {
                    showMessage(R.string.gesture_mode_blocked_auto_scroll_enabled_for_lock, MessageSeverity.Warning)
                    return
                }
                else -> enableLockForNextGesture(showMessage = true)
            }
        } else {
            showMessage(R.string.gesture_lock_disabled, MessageSeverity.Info)
            disableLockInternal(allowAutoReenable = true)
        }
    }

    /**
     * Explicitly disable the gesture lock.
     */
    fun disableLock(allowAutoReenable: Boolean = true, showMessage: Boolean = false) {
        if (isLocked) {
            if (showMessage) {
                showMessage(R.string.gesture_lock_disabled, MessageSeverity.Info)
            }
            disableLockInternal(allowAutoReenable)
        }
    }

    /**
     * Check if the gesture lock is engaged.
     *
     * @return true if the gesture lock is engaged, false otherwise.
     */
    fun isGestureLockEngaged(): Boolean {
        return isLocked && lockedGestureData != null
    }

    /**
     * Check if lock is enabled.
     *
     * @return true if lock is enabled, false otherwise.
     */
    fun isLocked() = isLocked

    fun isAutoReenableEnabled(): Boolean {
        return autoReenableEnabled
    }

    fun isAutoReenableEnabled(context: Context): Boolean {
        return isAutoReenableEnabled()
    }

    /**
     * Get the locked gesture data.
     *
     * @return the locked gesture data, or null if the gesture lock is not engaged.
     */
    fun getLockedGestureData(): GestureData? {
        return lockedGestureData
    }

    /**
     * Set the locked gesture data.
     *
     * @param gestureData the locked gesture data, or null to clear the lock.
     */
    fun setLockedGestureData(gestureData: GestureData?) {
        lockedGestureData =
            if (gestureData != null && isLocked) {
                gestureData
            } else {
                null
            }
        if (lockedGestureData != null) {
            Tasks.getInstance().onOngoingTaskStarted()
            startTimer()
        } else {
            stopTimer()
        }
    }

    /**
     * Start the timer for the gesture lock.
     */
    fun startTimer() {
        stopTimer()
        timeoutTimer = Timer()
        timeoutTimer?.schedule(object : TimerTask() {
            override fun run() {
                handleLockTimeout()
            }
        }, lockTimeout)
    }

    /**
     * Stop the timer for the gesture lock.
     */
    fun stopTimer() {
        timeoutTimer?.cancel()
        timeoutTimer = null
    }

    fun enableLockForNextGesture(showMessage: Boolean) {
        isLocked = true
        lockedGestureData = null
        stopTimer()
        if (showMessage) {
            showMessage(R.string.gesture_lock_enabled, MessageSeverity.Info)
        }
    }

    fun clearServiceState(showMessage: Boolean = false) {
        val wasReenableEnabled = autoReenableEnabled
        isLocked = false
        lockedGestureData = null
        autoReenableEnabled = false
        stopTimer()
        if (showMessage && wasReenableEnabled) {
            showMessage(R.string.gesture_lock_rearm_disabled, MessageSeverity.Info)
        }
    }

    private fun disableLockInternal(allowAutoReenable: Boolean) {
        val hadGesture = lockedGestureData != null
        isLocked = false
        lockedGestureData = null
        stopTimer()
        if (allowAutoReenable && hadGesture && isAutoReenableEnabled()) {
            isLocked = true
            showMessage(R.string.gesture_lock_rearmed, MessageSeverity.Info)
        }
    }

    private fun handleLockTimeout() {
        val hadGesture = lockedGestureData != null
        isLocked = false
        lockedGestureData = null
        stopTimer()
        showMessage(R.string.gesture_lock_timeout_disabled, MessageSeverity.Warning)
        if (hadGesture && isAutoReenableEnabled()) {
            isLocked = true
            showMessage(R.string.gesture_lock_rearmed, MessageSeverity.Info)
        }
    }

    private fun isAutoRepeatEnabled(): Boolean {
        return GestureRepeatManager.instance.isAutoRepeatEnabled()
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

    internal fun handleLockTimeoutForTesting() {
        handleLockTimeout()
    }

    internal fun setAutoReenableEnabledForTesting(enabled: Boolean) {
        autoReenableEnabled = enabled
    }

    internal fun setSuppressHudForTesting(suppress: Boolean) {
        suppressHudForTesting = suppress
    }

    internal fun setMessageRecorderForTesting(recorder: ((Int) -> Unit)?) {
        messageRecorderForTesting = recorder
    }

    internal fun toggleAutoReenableForTesting(syncGestureLock: Boolean) {
        val nextEnabled = !isAutoReenableEnabled()
        val result = GestureModePolicy.setRearmEnabled(
            nextEnabled,
            currentRepeatEnabled = GestureRepeatManager.instance.isAutoRepeatEnabled(),
            currentRearmEnabled = autoReenableEnabled,
            isGestureLockEnabled = isLocked(),
            isAutoScrollActive = AutoScrollManager.getInstance().isAutoScrolling()
        )
        result.blockedReasonResId?.let {
            showMessage(it, MessageSeverity.Warning)
            return
        }
        applyAutoReenableToggleResult(result.state.rearmEnabled, syncGestureLock)
    }

    internal fun resetForTesting() {
        isLocked = false
        lockedGestureData = null
        stopTimer()
        accessibilityService = null
        autoReenableEnabled = false
        suppressHudForTesting = false
        messageRecorderForTesting = null
    }
}
