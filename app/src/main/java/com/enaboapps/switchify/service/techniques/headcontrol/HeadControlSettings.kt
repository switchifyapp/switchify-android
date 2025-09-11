package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture

class HeadControlSettings(context: Context) {
    private val prefs = PreferenceManager(context.applicationContext)

    fun sensitivity(): Float = prefs.getFloatValue(KEY_SENSITIVITY, 0.8f).coerceIn(0.1f, 2.0f)
    
    fun deadzone(): Float = prefs.getFloatValue(KEY_DEADZONE, 0.5f).coerceIn(0.1f, 30.0f)
    
    fun isAbsoluteMode(): Boolean = prefs.getBooleanValue(KEY_ABSOLUTE_MODE, true)
    
    fun movementSpeed(): Float = prefs.getFloatValue(KEY_MOVEMENT_SPEED, 2.0f).coerceIn(0.5f, 5.0f)
    
    fun horizontalDeadzone(): Float = prefs.getFloatValue(KEY_HORIZONTAL_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun verticalDeadzone(): Float = prefs.getFloatValue(KEY_VERTICAL_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun leftDeadzone(): Float = prefs.getFloatValue(KEY_LEFT_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun rightDeadzone(): Float = prefs.getFloatValue(KEY_RIGHT_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun upDeadzone(): Float = prefs.getFloatValue(KEY_UP_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun downDeadzone(): Float = prefs.getFloatValue(KEY_DOWN_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun useSeparateDirectionalThresholds(): Boolean = prefs.getBooleanValue(KEY_SEPARATE_DIRECTIONAL_THRESHOLDS, false)
    
    fun getEffectiveLeftDeadzone(): Float {
        return if (useSeparateDirectionalThresholds()) {
            leftDeadzone()
        } else {
            deadzone()
        }
    }
    
    fun getEffectiveRightDeadzone(): Float {
        return if (useSeparateDirectionalThresholds()) {
            rightDeadzone()
        } else {
            deadzone()
        }
    }
    
    fun getEffectiveUpDeadzone(): Float {
        return if (useSeparateDirectionalThresholds()) {
            upDeadzone()
        } else {
            deadzone()
        }
    }
    
    fun getEffectiveDownDeadzone(): Float {
        return if (useSeparateDirectionalThresholds()) {
            downDeadzone()
        } else {
            deadzone()
        }
    }
    
    // Legacy methods for backward compatibility
    fun getEffectiveHorizontalDeadzone(): Float {
        return if (useSeparateDirectionalThresholds()) {
            (leftDeadzone() + rightDeadzone()) / 2f
        } else {
            deadzone()
        }
    }
    
    fun getEffectiveVerticalDeadzone(): Float {
        return if (useSeparateDirectionalThresholds()) {
            (upDeadzone() + downDeadzone()) / 2f
        } else {
            deadzone()
        }
    }
    
    fun baseStep(): Int = prefs.getIntegerValue(KEY_BASE_STEP, 25).coerceIn(5, 100)
    
    fun selectGesture(): String = prefs.getStringValue(KEY_SELECT_GESTURE, CameraSwitchFacialGesture.SMILE)
    
    fun isGestureSelectionEnabled(): Boolean = prefs.getBooleanValue(KEY_GESTURE_SELECTION_ENABLED, true)
    
    fun isHeadControlPriorityEnabled(): Boolean = prefs.getBooleanValue(KEY_GESTURE_PRIORITY_HEAD_CONTROL, true)
    
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

    fun isHeadControlEnabled(): Boolean = prefs.getBooleanValue(KEY_ENABLED, false)
    
    fun setHeadControlEnabled(enabled: Boolean) {
        prefs.setBooleanValue(KEY_ENABLED, enabled)
    }

    fun menuRepeatInitialDelay(): Long =
        prefs.getLongValue(KEY_MENU_REPEAT_INITIAL_DELAY, 600L).coerceIn(200L, 2000L)

    fun menuRepeatInterval(): Long =
        prefs.getLongValue(KEY_MENU_REPEAT_INTERVAL, 250L).coerceIn(80L, 1500L)

    companion object {
        const val KEY_ENABLED = "head_control_enabled"
        const val KEY_SENSITIVITY = "head_control_sensitivity"
        const val KEY_DEADZONE = "head_control_deadzone"
        const val KEY_BASE_STEP = "head_control_base_step"
        const val KEY_SELECT_GESTURE = "head_control_select_gesture"
        const val KEY_GESTURE_SELECTION_ENABLED = "head_control_gesture_selection_enabled"
        const val KEY_GESTURE_HOLD_TIME = "head_control_gesture_hold_time"
        const val KEY_GESTURE_PRIORITY_HEAD_CONTROL = "head_control_gesture_priority_head_control"
        const val KEY_ABSOLUTE_MODE = "head_control_absolute_mode"
        const val KEY_MOVEMENT_SPEED = "head_control_movement_speed"
        const val KEY_HORIZONTAL_DEADZONE = "head_control_horizontal_deadzone"
        const val KEY_VERTICAL_DEADZONE = "head_control_vertical_deadzone"
        const val KEY_LEFT_DEADZONE = "head_control_left_deadzone"
        const val KEY_RIGHT_DEADZONE = "head_control_right_deadzone"
        const val KEY_UP_DEADZONE = "head_control_up_deadzone"
        const val KEY_DOWN_DEADZONE = "head_control_down_deadzone"
        const val KEY_SEPARATE_DIRECTIONAL_THRESHOLDS = "head_control_separate_directional_thresholds"
        const val KEY_MENU_REPEAT_INITIAL_DELAY = "head_control_menu_repeat_initial_delay"
        const val KEY_MENU_REPEAT_INTERVAL = "head_control_menu_repeat_interval"
        
        // Centralized value arrays for UI
        val SENSITIVITY_VALUES = floatArrayOf(0.1f, 0.3f, 0.5f, 0.8f, 1.0f, 1.3f, 1.6f, 2.0f)
        val DEADZONE_VALUES = floatArrayOf(0.1f, 0.3f, 0.5f, 0.8f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f)
        val DIRECTIONAL_DEADZONE_VALUES = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 12.0f, 15.0f, 20.0f, 25.0f, 30.0f)
        val MOVEMENT_SPEED_VALUES = floatArrayOf(0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 4.5f, 5.0f)
        val HOLD_TIME_VALUES = longArrayOf(100L, 300L, 500L, 750L, 1000L, 1500L, 2000L)
        val MENU_REPEAT_INITIAL_VALUES = longArrayOf(300L, 450L, 600L, 800L, 1000L)
        val MENU_REPEAT_INTERVAL_VALUES = longArrayOf(120L, 180L, 250L, 350L, 500L)
    }
}
