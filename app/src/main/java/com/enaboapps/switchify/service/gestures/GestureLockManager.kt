package com.enaboapps.switchify.service.gestures

import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import java.util.Timer
import java.util.TimerTask

class GestureLockManager private constructor() {
    private var isLocked = false
    private var lockedGestureData: GestureData? = null
    private var timeoutTimer: Timer? = null
    private val lockTimeout = 60000L

    companion object {
        private var instance: GestureLockManager? = null

        /**
         * Gets the singleton instance of the GestureLockManager.
         * @return The singleton instance of the GestureLockManager.
         */
        fun getInstance(): GestureLockManager {
            return instance ?: synchronized(this) {
                instance ?: GestureLockManager().also { instance = it }
            }
        }
    }

    // Function to lock/unlock the gesture lock, showing a message to the user
    fun toggleGestureLock() {
        if (!IAPHandler.hasPurchasedPro()) {
            ServiceMessageHUD.instance.showMessage(
                R.string.pro_feature_message,
                arrayOf(R.string.system_gesture_lock),
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
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
        return isLocked && !AccessTechnique.isInMenu && lockedGestureData != null
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
            if (gestureData != null && canLockGesture(gestureData.gestureType) && isLocked) {
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

    /**
     * Check if a gesture type can be locked.
     *
     * @param gestureType the gesture type to check.
     * @return true if the gesture type can be locked, false otherwise.
     */
    fun canLockGesture(gestureType: GestureType): Boolean {
        if (isLocked && gestureType == GestureType.DRAG || gestureType == GestureType.CUSTOM_SWIPE) {
            isLocked = false // Disable the gesture lock
            setLockedGestureData(null) // Clear the locked gesture data
            return false
        }
        return true
    }

    /**
     * Inform the user that the type of gesture cannot be locked.
     *
     * @param type the gesture type that cannot be locked.
     */
    fun informCannotLockGesture(type: GestureType) {
        if (!canLockGesture(type)) {
            ServiceMessageHUD.instance.showMessage(
                R.string.gesture_lock_invalid_gesture,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
        }
    }
}
