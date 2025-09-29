package com.enaboapps.switchify.service.gestures.data

import android.graphics.PointF
import com.enaboapps.switchify.service.gestures.GestureManager

data class GestureData(
    val gestureType: GestureType,
    val startPoint: PointF,
    val endPoint: PointF? = null,
    val fingerCount: Int = 1,
    val fingerMode: com.enaboapps.switchify.service.gestures.placement.FingerMode = com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE
) {
    companion object {
        const val TAP_DURATION = 100L
        const val DOUBLE_TAP_INTERVAL = 250L
        const val TAP_AND_HOLD_DURATION = 1000L
        const val SWIPE_DURATION = 80L
        const val DRAG_DURATION = 1500L
        const val HOLD_BEFORE_DRAG_DURATION = 400L
        const val SCROLL_DURATION = 800L
        const val ZOOM_DURATION = 500L
    }

    fun duration(): Long = when (gestureType) {
        GestureType.TAP -> TAP_DURATION
        GestureType.DOUBLE_TAP -> DOUBLE_TAP_INTERVAL
        GestureType.TAP_AND_HOLD -> TAP_AND_HOLD_DURATION
        GestureType.SWIPE_UP,
        GestureType.SWIPE_DOWN,
        GestureType.SWIPE_LEFT,
        GestureType.SWIPE_RIGHT,
        GestureType.CUSTOM_SWIPE -> SWIPE_DURATION

        GestureType.DRAG,
        GestureType.HOLD_AND_DRAG -> DRAG_DURATION

        GestureType.ZOOM_IN,
        GestureType.ZOOM_OUT -> ZOOM_DURATION

        GestureType.SCROLL_UP,
        GestureType.SCROLL_DOWN,
        GestureType.SCROLL_LEFT,
        GestureType.SCROLL_RIGHT -> SCROLL_DURATION
    }


    fun isScroll(): Boolean {
        return gestureType == GestureType.SCROLL_UP ||
                gestureType == GestureType.SCROLL_DOWN ||
                gestureType == GestureType.SCROLL_LEFT ||
                gestureType == GestureType.SCROLL_RIGHT
    }

    fun performAutoScroll(): Boolean {
        when (gestureType) {
            GestureType.SCROLL_UP, GestureType.SCROLL_DOWN, GestureType.SCROLL_LEFT, GestureType.SCROLL_RIGHT -> {
                GestureManager.instance.performSwipeOrScroll(gestureType)
                return true
            }

            else -> {
                return false
            }
        }
    }

    fun executeGesture() {
        when (gestureType) {
            GestureType.TAP -> {
                GestureManager.instance.performTap(
                    x = startPoint.x.toInt(),
                    y = startPoint.y.toInt()
                )
            }

            GestureType.DOUBLE_TAP -> {
                GestureManager.instance.performDoubleTap(
                    x = startPoint.x.toInt(),
                    y = startPoint.y.toInt()
                )
            }

            GestureType.TAP_AND_HOLD -> {
                GestureManager.instance.performTapAndHold(
                    x = startPoint.x.toInt(),
                    y = startPoint.y.toInt()
                )
            }

            GestureType.SWIPE_UP,
            GestureType.SWIPE_DOWN,
            GestureType.SWIPE_LEFT,
            GestureType.SWIPE_RIGHT,
            GestureType.SCROLL_UP,
            GestureType.SCROLL_DOWN,
            GestureType.SCROLL_LEFT,
            GestureType.SCROLL_RIGHT -> {
                GestureManager.instance.performSwipeOrScroll(gestureType, startPoint)
            }

            GestureType.CUSTOM_SWIPE,
            GestureType.DRAG,
            GestureType.HOLD_AND_DRAG -> {
                GestureManager.instance.performCustomGestureAction(this)
            }

            GestureType.ZOOM_IN, GestureType.ZOOM_OUT -> {
                GestureManager.instance.performZoom(gestureType, startPoint)
            }
        }
    }
}