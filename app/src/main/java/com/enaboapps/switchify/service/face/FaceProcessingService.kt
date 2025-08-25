package com.enaboapps.switchify.service.face

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.google.mlkit.vision.face.Face

/**
 * Centralized face processing service that handles gesture detection logic.
 * Used by both CameraSwitchManager and CameraSettingsScreenModel to ensure consistency.
 */
class FaceProcessingService(context: Context) {
    
    private val preferenceManager = PreferenceManager(context)
    
    companion object {
        const val SMILE_THRESHOLD = 0.5f
        const val EYE_OPEN_THRESHOLD = 0.2f
    }
    
    /**
     * Data class representing the current state of detected facial gestures
     */
    data class FaceDetectionResult(
        val detectedGestures: Set<String>,
        val faceState: FaceState
    )
    
    /**
     * Data class representing the raw face state for comparison
     */
    data class FaceState(
        val leftEyeOpen: Boolean = true,
        val rightEyeOpen: Boolean = true,
        val isSmiling: Boolean = false,
        val headRotationY: Float = 0f,
        val headRotationX: Float = 0f
    )
    
    /**
     * Processes a face and returns detected gestures using current preference settings
     */
    fun processFace(face: Face): FaceDetectionResult {
        val detectedGestures = mutableSetOf<String>()
        
        // Extract basic face state
        val leftEyeOpen = (face.leftEyeOpenProbability ?: 1f) > EYE_OPEN_THRESHOLD
        val rightEyeOpen = (face.rightEyeOpenProbability ?: 1f) > EYE_OPEN_THRESHOLD
        val isSmiling = (face.smilingProbability ?: 0f) > SMILE_THRESHOLD
        val headRotationY = face.headEulerAngleY
        val headRotationX = face.headEulerAngleX
        
        // Create face state object
        val faceState = FaceState(
            leftEyeOpen = leftEyeOpen,
            rightEyeOpen = rightEyeOpen,
            isSmiling = isSmiling,
            headRotationY = headRotationY,
            headRotationX = headRotationX
        )
        
        // Check for smile
        if (isSmiling) {
            detectedGestures.add(CameraSwitchFacialGesture.SMILE)
        }
        
        // Check for eye-based gestures
        // Left wink (left eye closed, right eye open)
        if (!leftEyeOpen && rightEyeOpen) {
            detectedGestures.add(CameraSwitchFacialGesture.LEFT_WINK)
        }
        
        // Right wink (right eye closed, left eye open)
        if (leftEyeOpen && !rightEyeOpen) {
            detectedGestures.add(CameraSwitchFacialGesture.RIGHT_WINK)
        }
        
        // Blink (both eyes closed)
        if (!leftEyeOpen && !rightEyeOpen) {
            detectedGestures.add(CameraSwitchFacialGesture.BLINK)
        }
        
        // Check for head turns using current preference settings
        val leftThreshold = getHeadTurnLeftThreshold()
        val rightThreshold = getHeadTurnRightThreshold()
        val upThreshold = getHeadTurnUpThreshold()
        val downThreshold = getHeadTurnDownThreshold()
        
        // Head turn left (positive Y rotation)
        if (headRotationY > leftThreshold) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_LEFT)
        }
        
        // Head turn right (negative Y rotation)
        if (headRotationY < -rightThreshold) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_RIGHT)
        }
        
        // Head turn up (positive X rotation)
        if (headRotationX > upThreshold) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_UP)
        }
        
        // Head turn down (negative X rotation)
        if (headRotationX < -downThreshold) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_DOWN)
        }
        
        return FaceDetectionResult(detectedGestures, faceState)
    }
    
    /**
     * Gets the required hold time for a specific gesture from preferences
     */
    fun getGestureTime(gestureId: String): Long {
        return when (gestureId) {
            CameraSwitchFacialGesture.SMILE -> 
                preferenceManager.getLongValue(PreferenceManager.PREFERENCE_KEY_CAMERA_SMILE_TIME, 500L)
            CameraSwitchFacialGesture.LEFT_WINK -> 
                preferenceManager.getLongValue(PreferenceManager.PREFERENCE_KEY_CAMERA_LEFT_WINK_TIME, 300L)
            CameraSwitchFacialGesture.RIGHT_WINK -> 
                preferenceManager.getLongValue(PreferenceManager.PREFERENCE_KEY_CAMERA_RIGHT_WINK_TIME, 300L)
            CameraSwitchFacialGesture.BLINK -> 
                preferenceManager.getLongValue(PreferenceManager.PREFERENCE_KEY_CAMERA_BLINK_TIME, 400L)
            else -> 500L // Default fallback
        }
    }
    
    // Head turn threshold getters using global preferences
    private fun getHeadTurnLeftThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_LEFT_SENSITIVITY, 4)
        return CameraSwitchManager.getHeadTurnThreshold(sensitivity)
    }
    
    private fun getHeadTurnRightThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_RIGHT_SENSITIVITY, 4)
        return CameraSwitchManager.getHeadTurnThreshold(sensitivity)
    }
    
    private fun getHeadTurnUpThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_UP_SENSITIVITY, 4)
        return CameraSwitchManager.getHeadTurnThreshold(sensitivity)
    }
    
    private fun getHeadTurnDownThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_DOWN_SENSITIVITY, 4)
        return CameraSwitchManager.getHeadTurnThreshold(sensitivity)
    }
}