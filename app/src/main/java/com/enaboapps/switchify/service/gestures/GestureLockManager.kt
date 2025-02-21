package com.enaboapps.switchify.service.gestures

import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.scanning.ScanMethod
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import java.util.Timer
import java.util.TimerTask

class GestureLockManager {
    private var isLocked = false
    private var lockedGestureData: GestureData? = null
    private var timeoutTimer: Timer? = null
    private val lockTimeout = 60000L

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

    // Function to check if the gesture lock is enabled and the user is not in the menu
    // and the locked gesture data is not null
    fun isGestureLockEnabled(): Boolean {
        return isLocked && !ScanMethod.isInMenu && lockedGestureData != null
    }

    // Function to get the locked gesture data
    fun getLockedGestureData(): GestureData? {
        return lockedGestureData
    }

    // Function to set the locked gesture data
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

    // Function to start the timer for the gesture lock
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

    fun stopTimer() {
        timeoutTimer?.cancel()
        timeoutTimer = null
    }

    // Function to check if a gesture type can be locked
    fun canLockGesture(gestureType: GestureType): Boolean {
        if (isLocked && gestureType == GestureType.DRAG || gestureType == GestureType.CUSTOM_SWIPE) {
            isLocked = false // Disable the gesture lock
            setLockedGestureData(null) // Clear the locked gesture data
            return false
        }
        return true
    }

    // Function to inform the user that the type of gesture cannot be locked
    fun informCannotLockGesture(type: GestureType) {
        if (!canLockGesture(type)) {
            ServiceMessageHUD.instance.showMessage(
                R.string.gesture_lock_invalid_gesture,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
        }
    }
}
