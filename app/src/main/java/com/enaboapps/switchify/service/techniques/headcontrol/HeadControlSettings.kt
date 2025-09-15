package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture

/**
 * Represents the validation state of head control gesture settings
 */
enum class GestureValidationResult {
    /** All gesture settings are valid */
    VALID,
    /** Select and menu gestures are the same (conflict) */
    DUPLICATE_GESTURES,
    /** Selection gesture is not valid for head control */
    INVALID_SELECT_GESTURE,
    /** Menu gesture is not valid for head control */
    INVALID_MENU_GESTURE
}

/**
 * Manages head control settings and preferences with gesture validation
 * @param context Application context for preference access
 */
class HeadControlSettings(context: Context) {
    private val prefs = PreferenceManager(context.applicationContext)

    /**
     * Get head control sensitivity setting
     * @return sensitivity value between 0.1 and 2.0
     */
    fun sensitivity(): Float = prefs.getFloatValue(KEY_SENSITIVITY, 0.8f).coerceIn(0.1f, 2.0f)
    
    /**
     * Get global deadzone setting (used when separate directional thresholds are disabled)
     * @return deadzone value in degrees between 0.1 and 30.0
     */
    fun deadzone(): Float = prefs.getFloatValue(KEY_DEADZONE, 0.5f).coerceIn(0.1f, 30.0f)
    
    
    /**
     * Get cursor movement speed multiplier
     * @return movement speed between 0.5 and 5.0
     */
    fun movementSpeed(): Float = prefs.getFloatValue(KEY_MOVEMENT_SPEED, 2.0f).coerceIn(0.5f, 5.0f)
    
    fun horizontalDeadzone(): Float = prefs.getFloatValue(KEY_HORIZONTAL_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun verticalDeadzone(): Float = prefs.getFloatValue(KEY_VERTICAL_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun leftDeadzone(): Float = prefs.getFloatValue(KEY_LEFT_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun rightDeadzone(): Float = prefs.getFloatValue(KEY_RIGHT_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun upDeadzone(): Float = prefs.getFloatValue(KEY_UP_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    fun downDeadzone(): Float = prefs.getFloatValue(KEY_DOWN_DEADZONE, 1.0f).coerceIn(0.1f, 30.0f)
    
    /**
     * Check if separate directional thresholds are enabled
     * @return true if using separate thresholds for each direction
     */
    fun useSeparateDirectionalThresholds(): Boolean = prefs.getBooleanValue(KEY_SEPARATE_DIRECTIONAL_THRESHOLDS, false)
    
    /**
     * Get the effective left deadzone based on current threshold settings
     * @return left deadzone value in degrees
     */
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
    
    
    fun baseStep(): Int = prefs.getIntegerValue(KEY_BASE_STEP, 25).coerceIn(5, 100)
    
    /**
     * Get the currently configured selection gesture
     * @return gesture ID for selection action
     */
    fun selectGesture(): String = prefs.getStringValue(KEY_SELECT_GESTURE, CameraSwitchFacialGesture.SMILE)
    
    /**
     * Get the currently configured menu gesture
     * @return gesture ID for menu action
     */
    fun menuGesture(): String = prefs.getStringValue(KEY_MENU_GESTURE, CameraSwitchFacialGesture.LEFT_WINK)
    
    /**
     * Set selection gesture with automatic conflict resolution
     * @param gestureId The facial gesture ID to set for selection
     * @return true if the gesture was set successfully
     */
    fun setSelectGesture(gestureId: String): Boolean {
        prefs.setStringValue(KEY_SELECT_GESTURE, gestureId)
        // Only attempt resolution if there's actually a conflict
        if (gestureId == menuGesture()) {
            return resolveGestureConflicts()
        }
        return true
    }
    
    /**
     * Set menu gesture with validation
     * @param gestureId The facial gesture ID to set for menu trigger
     * @return true if the gesture was set successfully, false if blocked due to conflict
     */
    fun setMenuGesture(gestureId: String): Boolean {
        // Only set if it doesn't conflict with current selection gesture
        if (gestureId != selectGesture()) {
            prefs.setStringValue(KEY_MENU_GESTURE, gestureId)
            return true
        }
        return false
    }
    
    
    /**
     * Check if head control has gesture priority
     * @return true if head control gestures take priority
     */
    fun isHeadControlPriorityEnabled(): Boolean = prefs.getBooleanValue(KEY_GESTURE_PRIORITY_HEAD_CONTROL, true)
    
    /**
     * Get the hold time required for selection gesture activation
     * @return hold time in milliseconds between 100 and 2000
     */
    fun getSelectGestureHoldTime(): Long = prefs.getLongValue(KEY_SELECT_GESTURE_HOLD_TIME, 500L).coerceIn(100L, 2000L)
    
    /**
     * Get the hold time required for menu gesture activation
     * @return hold time in milliseconds between 100 and 2000
     */
    fun getMenuGestureHoldTime(): Long = prefs.getLongValue(KEY_MENU_GESTURE_HOLD_TIME, 750L).coerceIn(100L, 2000L)
    
    /**
     * Get available gestures for selection (excludes head turns which are used for cursor control)
     */
    fun getAvailableSelectGestures(): List<String> {
        return listOf(
            CameraSwitchFacialGesture.SMILE,
            CameraSwitchFacialGesture.LEFT_WINK,
            CameraSwitchFacialGesture.RIGHT_WINK,
            CameraSwitchFacialGesture.BLINK,
            CameraSwitchFacialGesture.TONGUE_OUT
        )
    }
    
    /**
     * Get available gestures for menu trigger (excludes current selection gesture)
     */
    fun getAvailableMenuGestures(): List<String> {
        return getAvailableSelectGestures().filter { it != selectGesture() }
    }
    
    /**
     * Check if the given gesture is valid for selection in head control
     */
    fun isValidSelectGesture(gestureId: String): Boolean {
        return getAvailableSelectGestures().contains(gestureId)
    }
    
    /**
     * Check if the given gesture is valid for menu trigger in head control
     */
    fun isValidMenuGesture(gestureId: String): Boolean {
        return getAvailableSelectGestures().contains(gestureId) && gestureId != selectGesture()
    }
    
    /**
     * Check if there's a conflict between select and menu gestures
     */
    /**
     * Check if there's a conflict between select and menu gestures
     * @return true if gestures are the same (conflict exists)
     */
    fun hasGestureConflict(): Boolean {
        return selectGesture() == menuGesture()
    }
    
    /**
     * Validate and auto-resolve gesture conflicts by changing menu gesture
     * @return true if a conflict was resolved, false if no conflict existed
     */
    fun resolveGestureConflicts(): Boolean {
        if (!hasGestureConflict()) {
            return false
        }
        
        // Find the first available gesture that's different from the select gesture
        val availableMenuGestures = getAvailableMenuGestures()
        if (availableMenuGestures.isNotEmpty()) {
            prefs.setStringValue(KEY_MENU_GESTURE, availableMenuGestures[0])
            return true
        }
        
        return false
    }
    
    /**
     * Validate gesture settings and return any conflicts
     * @return GestureValidationResult indicating the validation status
     */
    fun validateGestureSettings(): GestureValidationResult {
        val selectGest = selectGesture()
        val menuGest = menuGesture()
        
        return when {
            selectGest == menuGest -> GestureValidationResult.DUPLICATE_GESTURES
            !isValidSelectGesture(selectGest) -> GestureValidationResult.INVALID_SELECT_GESTURE
            !isValidMenuGesture(menuGest) -> GestureValidationResult.INVALID_MENU_GESTURE
            else -> GestureValidationResult.VALID
        }
    }

    fun isHeadControlEnabled(): Boolean = prefs.getBooleanValue(KEY_ENABLED, false)
    
    fun setHeadControlEnabled(enabled: Boolean) {
        prefs.setBooleanValue(KEY_ENABLED, enabled)
    }

    fun menuRepeatInitialDelay(): Long =
        prefs.getLongValue(KEY_MENU_REPEAT_INITIAL_DELAY, DEFAULT_MENU_REPEAT_INITIAL_DELAY)
            .coerceIn(MIN_MENU_REPEAT_INITIAL_DELAY, MAX_MENU_REPEAT_INITIAL_DELAY)

    fun menuRepeatInterval(): Long =
        prefs.getLongValue(KEY_MENU_REPEAT_INTERVAL, DEFAULT_MENU_REPEAT_INTERVAL)
            .coerceIn(MIN_MENU_REPEAT_INTERVAL, MAX_MENU_REPEAT_INTERVAL)

    companion object {
        // Default values
        const val DEFAULT_MENU_REPEAT_INITIAL_DELAY = 600L
        const val DEFAULT_MENU_REPEAT_INTERVAL = 250L
        
        // Min/max values
        const val MIN_MENU_REPEAT_INITIAL_DELAY = 200L
        const val MAX_MENU_REPEAT_INITIAL_DELAY = 2000L
        const val MIN_MENU_REPEAT_INTERVAL = 80L
        const val MAX_MENU_REPEAT_INTERVAL = 1500L
        
        const val KEY_ENABLED = "head_control_enabled"
        const val KEY_SENSITIVITY = "head_control_sensitivity"
        const val KEY_DEADZONE = "head_control_deadzone"
        const val KEY_BASE_STEP = "head_control_base_step"
        const val KEY_SELECT_GESTURE = "head_control_select_gesture"
        const val KEY_MENU_GESTURE = "head_control_menu_gesture"
        const val KEY_MENU_GESTURE_HOLD_TIME = "head_control_menu_gesture_hold_time"
        const val KEY_SELECT_GESTURE_HOLD_TIME = "head_control_select_gesture_hold_time"
        const val KEY_GESTURE_PRIORITY_HEAD_CONTROL = "head_control_gesture_priority_head_control"
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
        
        // User-friendly threshold levels with meaningful labels
        // Maps to actual degree values internally while showing descriptive labels to users
        val USER_FRIENDLY_THRESHOLD_LEVELS = arrayOf(
            "Very Sensitive",    // 0.3° - Small head movements
            "Sensitive",        // 1.0° - Slight movements needed  
            "Normal",           // 2.5° - Recommended default
            "Less Sensitive",   // 8.0° - Moderate movements
            "Least Sensitive"   // 20.0° - Large movements needed
        )
        
        // Corresponding degree values for user-friendly levels - better dispersal across 0.3° to 20° range
        val USER_FRIENDLY_THRESHOLD_VALUES = floatArrayOf(0.3f, 1.0f, 2.5f, 8.0f, 20.0f)
        
        // Legacy arrays maintained for backward compatibility
        val DEADZONE_VALUES = floatArrayOf(0.1f, 0.3f, 0.5f, 0.8f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f)
        val DIRECTIONAL_DEADZONE_VALUES = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 12.0f, 15.0f, 20.0f, 25.0f, 30.0f)
        val MOVEMENT_SPEED_VALUES = floatArrayOf(0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 4.5f, 5.0f)
        val HOLD_TIME_VALUES = longArrayOf(100L, 300L, 500L, 750L, 1000L, 1500L, 2000L)
        val MENU_REPEAT_INITIAL_VALUES = longArrayOf(300L, 450L, 600L, 800L, 1000L)
        val MENU_REPEAT_INTERVAL_VALUES = longArrayOf(120L, 180L, 250L, 350L, 500L)
        
        /**
         * Get user-friendly threshold index from degree value
         * Maps any degree value to the closest user-friendly level
         */
        fun getUserFriendlyThresholdIndex(degreeValue: Float): Int {
            return USER_FRIENDLY_THRESHOLD_VALUES.indexOfFirst { kotlin.math.abs(it - degreeValue) < 0.1f }
                .takeIf { it != -1 } ?: 2 // Default to "Normal" if no exact match
        }
        
        /**
         * Get degree value from user-friendly threshold index
         */
        fun getThresholdValueFromIndex(index: Int): Float {
            return if (index in 0 until USER_FRIENDLY_THRESHOLD_VALUES.size) {
                USER_FRIENDLY_THRESHOLD_VALUES[index]
            } else {
                USER_FRIENDLY_THRESHOLD_VALUES[2] // Default to "Normal"
            }
        }
    }
}
