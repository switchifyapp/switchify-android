package com.enaboapps.switchify.service.gestures.execution

import com.enaboapps.switchify.service.gestures.data.GestureType

object GestureExecutionPolicy {
    fun fingerCount(gestureType: GestureType, requestedFingerCount: Int): Int {
        return if (gestureType == GestureType.HOLD_AND_DRAG) {
            1
        } else {
            requestedFingerCount.coerceIn(1, 5)
        }
    }
}
