package com.enaboapps.switchify.service.gestures.data

import android.graphics.PointF
import com.enaboapps.switchify.service.gestures.GestureManager

data class GestureData(
    val gestureType: GestureType,
    val startPoint: PointF,
    val endPoint: PointF? = null
) {

    companion object {
        const val TAP_DURATION = 100L
        const val DOUBLE_TAP_INTERVAL = 250L
        const val TAP_AND_HOLD_DURATION = 1000L
        const val SWIPE_DURATION = 80L
        const val DRAG_DURATION = 1500L
        const val HOLD_BEFORE_DRAG_DURATION = 400L
        const val SCROLL_DURATION = 800L
    }

    fun isScroll(): Boolean {
        return gestureType == GestureType.SCROLL_UP ||
                gestureType == GestureType.SCROLL_DOWN ||
                gestureType == GestureType.SCROLL_LEFT ||
                gestureType == GestureType.SCROLL_RIGHT
    }

    fun performAutoScroll(gestureManager: GestureManager): Boolean {
        when (gestureType) {
            GestureType.SCROLL_UP, GestureType.SCROLL_DOWN, GestureType.SCROLL_LEFT, GestureType.SCROLL_RIGHT -> {
                gestureManager.performSwipeOrScroll(gestureType)
                return true
            }

            else -> {
                return false
            }
        }
        return false
    }

    fun performLockAction(gestureManager: GestureManager): Boolean {
        when (gestureType) {
            GestureType.TAP -> {
                gestureManager.performTap()
                return true
            }

            GestureType.DOUBLE_TAP -> {
                gestureManager.performDoubleTap()
                return true
            }

            GestureType.TAP_AND_HOLD -> {
                gestureManager.performTapAndHold()
                return true
            }

            GestureType.SWIPE_UP,
            GestureType.SWIPE_DOWN,
            GestureType.SWIPE_LEFT,
            GestureType.SWIPE_RIGHT,
            GestureType.SCROLL_UP,
            GestureType.SCROLL_DOWN,
            GestureType.SCROLL_LEFT,
            GestureType.SCROLL_RIGHT -> {
                gestureManager.performSwipeOrScroll(gestureType)
                return true
            }

            GestureType.ZOOM_IN, GestureType.ZOOM_OUT -> {
                gestureManager.performZoom(gestureType)
                return true
            }

            else -> {
                return false
            }
        }
    }
}