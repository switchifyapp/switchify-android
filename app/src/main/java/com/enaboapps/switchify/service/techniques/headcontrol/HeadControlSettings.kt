package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture

class HeadControlSettings(context: Context) {
    private val prefs = PreferenceManager(context.applicationContext)

    fun sensitivity(): Float = prefs.getFloatValue(KEY_SENSITIVITY, 0.8f).coerceIn(0.1f, 2.0f)
    
    fun deadzone(): Float = prefs.getFloatValue(KEY_DEADZONE, 0.5f).coerceIn(0.1f, 10.0f)
    
    fun baseStep(): Int = prefs.getIntegerValue(KEY_BASE_STEP, 25).coerceIn(5, 100)
    
    fun selectGesture(): String = prefs.getStringValue(KEY_SELECT_GESTURE, CameraSwitchFacialGesture.SMILE)
    
    fun isGestureSelectionEnabled(): Boolean = prefs.getBooleanValue(KEY_GESTURE_SELECTION_ENABLED, true)
    
    fun gestureHoldTime(): Long = prefs.getLongValue(KEY_GESTURE_HOLD_TIME, 500L).coerceIn(100L, 2000L)
    
    /**
     * Get available gestures for selection (excludes head turns which are used for cursor control)
     */
    fun getAvailableSelectGestures(): List<String> {
        return listOf(
            CameraSwitchFacialGesture.SMILE,
            CameraSwitchFacialGesture.LEFT_WINK,
            CameraSwitchFacialGesture.RIGHT_WINK,
            CameraSwitchFacialGesture.BLINK
        )
    }
    
    /**
     * Check if the given gesture is valid for selection in head control
     */
    fun isValidSelectGesture(gestureId: String): Boolean {
        return getAvailableSelectGestures().contains(gestureId)
    }

    companion object {
        const val KEY_SENSITIVITY = "head_control_sensitivity"
        const val KEY_DEADZONE = "head_control_deadzone"
        const val KEY_BASE_STEP = "head_control_base_step"
        const val KEY_SELECT_GESTURE = "head_control_select_gesture"
        const val KEY_GESTURE_SELECTION_ENABLED = "head_control_gesture_selection_enabled"
        const val KEY_GESTURE_HOLD_TIME = "head_control_gesture_hold_time"
    }
}