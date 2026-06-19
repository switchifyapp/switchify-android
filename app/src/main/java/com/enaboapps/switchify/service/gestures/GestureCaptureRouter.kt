package com.enaboapps.switchify.service.gestures

import com.enaboapps.switchify.service.gestures.data.GestureData

object GestureCaptureRouter {
    fun onGesturePerformed(gestureData: GestureData) {
        GestureLockManager.instance.setLockedGestureData(gestureData)
        GestureRepeatManager.instance.onGesturePerformed(gestureData)
    }
}
