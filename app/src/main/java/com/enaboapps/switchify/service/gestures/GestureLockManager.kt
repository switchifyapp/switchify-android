package com.enaboapps.switchify.service.gestures

import android.accessibilityservice.AccessibilityService
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.utils.ServiceUtils
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import java.util.Timer
import java.util.TimerTask

class GestureLockManager private constructor() {
    private var isLocked = false
    private var lockedGestureData: GestureData? = null
    private var timeoutTimer: Timer? = null
    private val lockTimeout = 60000L
    private var accessibilityService: AccessibilityService? = null

    companion object {
        val instance: GestureLockManager by lazy { GestureLockManager() }
    }

    fun init(service: AccessibilityService) {
        accessibilityService = service
    }

    // Function to lock/unlock the gesture lock, showing a message to the user
    fun toggleGestureLock() {
        if (!IAPHandler.hasPurchasedPro()) {
            ServiceMessageHUD.instance.showMessage(
                R.string.pro_feature_message,
                arrayOf(R.string.system_gesture_lock),
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
            accessibilityService?.let { service ->
                ServiceUtils().openProUpgrade(service)
            }
            return
        }

        stopTimer()

        isLocked = !isLocked
        if (isLocked) {
            ServiceMessageHUD.instance.showMessage(
                R.string.gesture_lock_enabled,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
        } else {
            ServiceMessageHUD.instance.showMessage(
                R.string.gesture_lock_disabled,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )

            // Clear the locked gesture data
            lockedGestureData = null
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
        if (isLocked) {
            startTimer()
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
                isLocked = false
                setLockedGestureData(null)
                ServiceMessageHUD.instance.showMessage(
                    R.string.gesture_lock_timeout_disabled,
                    ServiceMessageHUD.MessageType.DISAPPEARING
                )
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
}
