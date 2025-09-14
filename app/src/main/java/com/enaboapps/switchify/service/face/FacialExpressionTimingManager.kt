package com.enaboapps.switchify.service.face

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified manager for facial expression timing preferences
 * Eliminates the duplicate hold time logic between FaceProcessingService and CameraSwitchManager
 */
@Singleton
class FacialExpressionTimingManager @Inject constructor(
    private val preferenceManager: PreferenceManager
) {

    /**
     * Gets the required hold time for a specific facial expression from preferences
     * 
     * @param expressionId The facial expression identifier
     * @return Hold time in milliseconds, or 0 for instant triggers
     */
    fun getExpressionHoldTime(expressionId: String): Long {
        return when (expressionId) {
            CameraSwitchFacialGesture.SMILE ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_SMILE_TIME,
                    FacialExpressionConstants.DefaultHoldTimes.SMILE_HOLD_TIME
                )

            CameraSwitchFacialGesture.LEFT_WINK ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_LEFT_WINK_TIME,
                    FacialExpressionConstants.DefaultHoldTimes.LEFT_WINK_HOLD_TIME
                )

            CameraSwitchFacialGesture.RIGHT_WINK ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_RIGHT_WINK_TIME,
                    FacialExpressionConstants.DefaultHoldTimes.RIGHT_WINK_HOLD_TIME
                )

            CameraSwitchFacialGesture.BLINK ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_BLINK_TIME,
                    FacialExpressionConstants.DefaultHoldTimes.BLINK_HOLD_TIME
                )

            CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            CameraSwitchFacialGesture.HEAD_TURN_UP,
            CameraSwitchFacialGesture.HEAD_TURN_DOWN -> 
                FacialExpressionConstants.DefaultHoldTimes.HEAD_TURN_HOLD_TIME // Instant trigger, no hold time

            else -> FacialExpressionConstants.DefaultHoldTimes.DEFAULT_FALLBACK_TIME
        }
    }

    /**
     * Gets the required hold time for a facial expression object
     * 
     * @param expression The CameraSwitchFacialGesture object
     * @return Hold time in milliseconds, or 0 for instant triggers
     */
    fun getExpressionHoldTime(expression: CameraSwitchFacialGesture): Long {
        return getExpressionHoldTime(expression.id)
    }

    /**
     * Checks if a facial expression requires a hold time or triggers instantly
     * 
     * @param expressionId The facial expression identifier
     * @return true if the expression triggers instantly, false if it requires holding
     */
    fun isInstantTrigger(expressionId: String): Boolean {
        return getExpressionHoldTime(expressionId) == 0L
    }

    /**
     * Gets the head control gesture hold time from unified preference
     * This is separate from camera switch expressions as head control uses a single
     * preference for all expression types
     * 
     * @return Hold time in milliseconds for head control expressions
     */
    fun getHeadControlExpressionHoldTime(): Long {
        return preferenceManager.getLongValue(
            "head_control_gesture_hold_time",
            1500L
        )
    }

    /**
     * Updates a specific facial expression hold time in preferences
     * 
     * @param expressionId The facial expression identifier
     * @param holdTime New hold time in milliseconds
     * @return true if the preference was updated successfully, false otherwise
     */
    fun setExpressionHoldTime(expressionId: String, holdTime: Long): Boolean {
        val preferenceKey = when (expressionId) {
            CameraSwitchFacialGesture.SMILE -> PreferenceManager.PREFERENCE_KEY_CAMERA_SMILE_TIME
            CameraSwitchFacialGesture.LEFT_WINK -> PreferenceManager.PREFERENCE_KEY_CAMERA_LEFT_WINK_TIME
            CameraSwitchFacialGesture.RIGHT_WINK -> PreferenceManager.PREFERENCE_KEY_CAMERA_RIGHT_WINK_TIME
            CameraSwitchFacialGesture.BLINK -> PreferenceManager.PREFERENCE_KEY_CAMERA_BLINK_TIME
            else -> return false // Head turns and unknown expressions cannot be set
        }
        
        preferenceManager.setLongValue(preferenceKey, holdTime)
        return true
    }

    /**
     * Resets all facial expression hold times to their default values
     */
    fun resetToDefaults() {
        setExpressionHoldTime(CameraSwitchFacialGesture.SMILE, 
            FacialExpressionConstants.DefaultHoldTimes.SMILE_HOLD_TIME)
        setExpressionHoldTime(CameraSwitchFacialGesture.LEFT_WINK, 
            FacialExpressionConstants.DefaultHoldTimes.LEFT_WINK_HOLD_TIME)
        setExpressionHoldTime(CameraSwitchFacialGesture.RIGHT_WINK, 
            FacialExpressionConstants.DefaultHoldTimes.RIGHT_WINK_HOLD_TIME)
        setExpressionHoldTime(CameraSwitchFacialGesture.BLINK, 
            FacialExpressionConstants.DefaultHoldTimes.BLINK_HOLD_TIME)
    }

    /**
     * Gets all facial expression hold times as a map for bulk operations
     * 
     * @return Map of expression ID to hold time in milliseconds
     */
    fun getAllExpressionHoldTimes(): Map<String, Long> {
        return mapOf(
            CameraSwitchFacialGesture.SMILE to getExpressionHoldTime(CameraSwitchFacialGesture.SMILE),
            CameraSwitchFacialGesture.LEFT_WINK to getExpressionHoldTime(CameraSwitchFacialGesture.LEFT_WINK),
            CameraSwitchFacialGesture.RIGHT_WINK to getExpressionHoldTime(CameraSwitchFacialGesture.RIGHT_WINK),
            CameraSwitchFacialGesture.BLINK to getExpressionHoldTime(CameraSwitchFacialGesture.BLINK),
            CameraSwitchFacialGesture.HEAD_TURN_LEFT to getExpressionHoldTime(CameraSwitchFacialGesture.HEAD_TURN_LEFT),
            CameraSwitchFacialGesture.HEAD_TURN_RIGHT to getExpressionHoldTime(CameraSwitchFacialGesture.HEAD_TURN_RIGHT),
            CameraSwitchFacialGesture.HEAD_TURN_UP to getExpressionHoldTime(CameraSwitchFacialGesture.HEAD_TURN_UP),
            CameraSwitchFacialGesture.HEAD_TURN_DOWN to getExpressionHoldTime(CameraSwitchFacialGesture.HEAD_TURN_DOWN)
        )
    }
}