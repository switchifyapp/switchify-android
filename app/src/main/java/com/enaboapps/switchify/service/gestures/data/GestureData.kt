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
        
        if (safeFingerCount != fingerCount) {
            android.util.Log.w("GestureData", "Invalid finger count $fingerCount, using safe value $safeFingerCount")
        }
        
        if (safeFingerCount > 1) {
            // Multi-finger gesture: Use stored finger count directly
            executeMultiFingerGesture()
        } else {
            // Single-finger gesture: Use existing methods (safe to use current preference)
            executeSingleFingerGesture()
        }
    }
    
    /**
     * Executes single-finger gestures using standard GestureManager methods.
     * Since these are single-finger, current user preference doesn't matter.
     */
    private fun executeSingleFingerGesture() {
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
    
    /**
     * Executes multi-finger gestures using stored finger count.
     * This bypasses user preferences and uses the exact finger count from the pattern.
     * 
     * The GestureManager.performCustomGestureAction() method has been enhanced to respect
     * the fingerCount field in GestureData, ensuring pattern playback fidelity.
     */
    private fun executeMultiFingerGesture() {
        android.util.Log.d("GestureData", "Executing multi-finger gesture: $gestureType with $fingerCount fingers (stored count)")
        GestureManager.instance.performCustomGestureAction(this)
    }
}