package com.enaboapps.switchify.service.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.data.GestureData
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
    private var autoReenableProviderForTesting: (() -> Boolean)? = null
    private var autoReenableSetterForTesting: ((Boolean) -> Unit)? = null
    private var suppressHudForTesting = false
    private var messageRecorderForTesting: ((Int) -> Unit)? = null

    companion object {
        val instance: GestureLockManager by lazy { GestureLockManager() }
    }

    fun init(service: AccessibilityService) {
        accessibilityService = service
    }

    fun toggleAutoReenable(context: Context, syncGestureLock: Boolean = false) {
        val nextEnabled = !isAutoReenableEnabled(context)
        setAutoReenableEnabled(context, nextEnabled)
        applyAutoReenableToggleResult(nextEnabled, syncGestureLock)
    }

    private fun applyAutoReenableToggleResult(nextEnabled: Boolean, syncGestureLock: Boolean) {
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
            enableLockForNextGesture(showMessage = true)
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
            GestureRepeatManager.instance.onLockedGestureChanged(lockedGestureData)
            if (GestureRepeatManager.instance.isRepeating()) {
                stopTimer()
            } else {
                startTimer()
            }
        } else {
            stopTimer()
            GestureRepeatManager.instance.onLockedGestureChanged(null)
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
        GestureRepeatManager.instance.onLockedGestureChanged(null)
        if (showMessage) {
            showMessage(R.string.gesture_lock_enabled, MessageSeverity.Info)
        }
    }

    private fun disableLockInternal(allowAutoReenable: Boolean) {
        val hadGesture = lockedGestureData != null
        GestureRepeatManager.instance.stopRepeat(showMessage = false)
        isLocked = false
        lockedGestureData = null
        stopTimer()
        if (allowAutoReenable && hadGesture && isAutoReenableEnabled()) {
            isLocked = true
            showMessage(R.string.gesture_lock_rearmed, MessageSeverity.Info)
        }
    }

    private fun handleLockTimeout() {
        if (GestureRepeatManager.instance.isRepeating()) {
            stopTimer()
            return
        }
        val hadGesture = lockedGestureData != null
        GestureRepeatManager.instance.stopRepeat(showMessage = false)
        isLocked = false
        lockedGestureData = null
        stopTimer()
        showMessage(R.string.gesture_lock_timeout_disabled, MessageSeverity.Warning)
        if (hadGesture && isAutoReenableEnabled()) {
            isLocked = true
            showMessage(R.string.gesture_lock_rearmed, MessageSeverity.Info)
        }
    }

    private fun isAutoReenableEnabled(): Boolean {
        autoReenableProviderForTesting?.let { return it() }
        return accessibilityService?.let {
            PreferenceManager(it).getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_LOCK_AUTO_REENABLE,
                false
            )
        } ?: false
    }

    private fun isAutoReenableEnabled(context: Context): Boolean {
        autoReenableProviderForTesting?.let { return it() }
        return PreferenceManager(context).getBooleanValue(
            PreferenceManager.PREFERENCE_KEY_GESTURE_LOCK_AUTO_REENABLE,
            false
        )
    }

    private fun setAutoReenableEnabled(context: Context, enabled: Boolean) {
        autoReenableSetterForTesting?.let {
            it(enabled)
            return
        }
        PreferenceManager(context).setBooleanValue(
            PreferenceManager.PREFERENCE_KEY_GESTURE_LOCK_AUTO_REENABLE,
            enabled
        )
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

    internal fun setAutoReenableProviderForTesting(provider: (() -> Boolean)?) {
        autoReenableProviderForTesting = provider
    }

    internal fun setAutoReenableSetterForTesting(setter: ((Boolean) -> Unit)?) {
        autoReenableSetterForTesting = setter
    }

    internal fun setSuppressHudForTesting(suppress: Boolean) {
        suppressHudForTesting = suppress
    }

    internal fun setMessageRecorderForTesting(recorder: ((Int) -> Unit)?) {
        messageRecorderForTesting = recorder
    }

    internal fun toggleAutoReenableForTesting(syncGestureLock: Boolean) {
        val nextEnabled = !isAutoReenableEnabled()
        autoReenableSetterForTesting?.invoke(nextEnabled)
        applyAutoReenableToggleResult(nextEnabled, syncGestureLock)
    }

    internal fun resetForTesting() {
        isLocked = false
        lockedGestureData = null
        stopTimer()
        GestureRepeatManager.instance.stopRepeat(showMessage = false)
        accessibilityService = null
        autoReenableProviderForTesting = null
        autoReenableSetterForTesting = null
        suppressHudForTesting = false
        messageRecorderForTesting = null
    }
}
