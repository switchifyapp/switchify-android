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
        const val TAP_AND_HOLD_0_5S_DURATION = 500L
        const val TAP_AND_HOLD_1S_DURATION = 1000L
        const val TAP_AND_HOLD_2S_DURATION = 2000L
        const val TAP_AND_HOLD_3S_DURATION = 3000L
        const val TAP_AND_HOLD_5S_DURATION = 5000L
        const val TAP_AND_HOLD_10S_DURATION = 10000L
        const val SWIPE_DURATION = 80L
        const val DRAG_DURATION = 1500L
        const val SCROLL_DURATION = 800L
        const val PINCH_DURATION = 500L
    }

    fun duration(): Long = when (gestureType) {
        GestureType.TAP -> TAP_DURATION
        GestureType.DOUBLE_TAP -> DOUBLE_TAP_INTERVAL
        GestureType.TAP_AND_HOLD_0_5S -> TAP_AND_HOLD_0_5S_DURATION
        GestureType.TAP_AND_HOLD_1S -> TAP_AND_HOLD_1S_DURATION
        GestureType.TAP_AND_HOLD_2S -> TAP_AND_HOLD_2S_DURATION
        GestureType.TAP_AND_HOLD_3S -> TAP_AND_HOLD_3S_DURATION
        GestureType.TAP_AND_HOLD_5S -> TAP_AND_HOLD_5S_DURATION
        GestureType.TAP_AND_HOLD_10S -> TAP_AND_HOLD_10S_DURATION
        GestureType.SWIPE_UP,
        GestureType.SWIPE_DOWN,
        GestureType.SWIPE_LEFT,
        GestureType.SWIPE_RIGHT,
        GestureType.CUSTOM_SWIPE -> SWIPE_DURATION

        GestureType.DRAG -> DRAG_DURATION

        GestureType.PINCH_IN,
        GestureType.PINCH_OUT -> PINCH_DURATION

        GestureType.SCROLL_UP,
        GestureType.SCROLL_DOWN,
        GestureType.SCROLL_LEFT,
        GestureType.SCROLL_RIGHT -> SCROLL_DURATION
    }


    fun isScroll(): Boolean {
        return gestureType.isScrollGesture()
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

    /**
     * Executes gesture using stored finger count for accurate pattern playback.
     *
     * This method ensures that patterns are played back with the exact finger count
     * that was used during recording, regardless of the current user finger mode preference.
     * For multi-finger gestures (fingerCount > 1), this method uses direct gesture
     * execution with the stored finger placement to bypass user preference.
     */
    fun executeGesture() {
        // Validate finger count and fallback to safe defaults if corrupted
        val safeFingerCount = fingerCount.coerceIn(1, 5)

        // Create sanitized gesture data if finger count was invalid
        val sanitizedGesture = if (safeFingerCount != fingerCount) {
            android.util.Log.w(
                "GestureData",
                "Invalid finger count $fingerCount, using safe value $safeFingerCount"
            )
            this.copy(fingerCount = safeFingerCount)
        } else {
            this
        }

        // Only use finger mode override if the original data was valid
        val overrideFingerMode =
            if (safeFingerCount == fingerCount) sanitizedGesture.fingerMode else null

        // Always use the gesture methods with explicit finger mode override
        // This ensures pattern playback accuracy regardless of current user preference
        when (gestureType) {
            GestureType.TAP -> {
                GestureManager.instance.performTap(
                    x = sanitizedGesture.startPoint.x.toInt(),
                    y = sanitizedGesture.startPoint.y.toInt(),
                    overrideFingerMode = overrideFingerMode
                )
            }

            GestureType.DOUBLE_TAP -> {
                GestureManager.instance.performDoubleTap(
                    x = sanitizedGesture.startPoint.x.toInt(),
                    y = sanitizedGesture.startPoint.y.toInt(),
                    overrideFingerMode = overrideFingerMode
                )
            }

            GestureType.TAP_AND_HOLD_0_5S,
            GestureType.TAP_AND_HOLD_1S,
            GestureType.TAP_AND_HOLD_2S,
            GestureType.TAP_AND_HOLD_3S,
            GestureType.TAP_AND_HOLD_5S,
            GestureType.TAP_AND_HOLD_10S -> {
                GestureManager.instance.performTapAndHold(
                    x = sanitizedGesture.startPoint.x.toInt(),
                    y = sanitizedGesture.startPoint.y.toInt(),
                    duration = sanitizedGesture.duration(),
                    gestureType = gestureType,
                    overrideFingerMode = overrideFingerMode
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
                GestureManager.instance.performSwipeOrScroll(
                    gestureType,
                    sanitizedGesture.startPoint,
                    overrideFingerMode
                )
            }

            GestureType.CUSTOM_SWIPE,
            GestureType.DRAG -> {
                GestureManager.instance.performCustomGestureAction(sanitizedGesture)
            }

            GestureType.PINCH_IN, GestureType.PINCH_OUT -> {
                GestureManager.instance.performPinch(gestureType, startPoint)
            }
        }
    }
}