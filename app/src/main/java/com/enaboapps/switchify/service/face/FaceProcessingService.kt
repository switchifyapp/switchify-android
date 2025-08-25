package com.enaboapps.switchify.service.face

import android.content.Context
import android.graphics.Bitmap
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Centralized face processing service that handles gesture detection logic.
 * Used by both CameraSwitchManager and CameraSettingsScreenModel to ensure consistency.
 * Enhanced with MediaPipe support for improved accuracy.
 */
class FaceProcessingService(context: Context) {
    
    private val preferenceManager = PreferenceManager(context)
    private var faceLandmarker: FaceLandmarker? = null
    
    companion object {
        const val SMILE_THRESHOLD = 0.3f
        const val EYE_BLINK_THRESHOLD = 0.5f
    }
    
    init {
        initFaceLandmarker(context)
    }
    
    private fun initFaceLandmarker(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()
            
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(false)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.3f)
                .setMinFacePresenceConfidence(0.3f)
                .setMinTrackingConfidence(0.3f)
                .build()
            
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            android.util.Log.e("FaceProcessingService", "Failed to initialize MediaPipe FaceLandmarker", e)
            faceLandmarker = null
        }
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
        val headRotationX: Float = 0f,
        val blendShapes: FloatArray? = null
    )

    
    /**
     * Processes a face bitmap and returns detected gestures using MediaPipe
     */
    fun processFace(bitmap: Bitmap): FaceDetectionResult? {
        val landmarker = faceLandmarker ?: return null
        
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = landmarker.detect(mpImage)
            processLandmarkerResult(result)
        } catch (e: Exception) {
            android.util.Log.e("FaceProcessingService", "Error processing face", e)
            null
        }
    }
    
    private fun processLandmarkerResult(result: com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult): FaceDetectionResult {
        val detectedGestures = mutableSetOf<String>()
        
        if (result.faceLandmarks().isEmpty()) {
            return FaceDetectionResult(emptySet(), FaceState())
        }
        
        // Simplified approach - just detect basic face presence first
        var leftEyeOpen = true
        var rightEyeOpen = true 
        var isSmiling = false
        val headRotationX = 0f
        val headRotationY = 0f
        
        // Try to get blendshapes if available
        try {
            if (result.faceBlendshapes().isPresent && result.faceBlendshapes().get().isNotEmpty()) {
                val blendShapes = result.faceBlendshapes().get()[0]
                val blendShapeMap = mutableMapOf<String, Float>()
                
                for (i in 0 until blendShapes.size) {
                    val category = blendShapes[i]
                    blendShapeMap[category.categoryName()] = category.score()
                }
                
                val leftEyeBlink = blendShapeMap["eyeBlinkLeft"] ?: 0f
                val rightEyeBlink = blendShapeMap["eyeBlinkRight"] ?: 0f  
                val mouthSmileLeft = blendShapeMap["mouthSmileLeft"] ?: 0f
                val mouthSmileRight = blendShapeMap["mouthSmileRight"] ?: 0f
                
                leftEyeOpen = leftEyeBlink < EYE_BLINK_THRESHOLD
                rightEyeOpen = rightEyeBlink < EYE_BLINK_THRESHOLD
                isSmiling = (mouthSmileLeft + mouthSmileRight) / 2f > SMILE_THRESHOLD
            }
        } catch (e: Exception) {
            android.util.Log.e("FaceProcessingService", "Error processing blendshapes", e)
        }
        
        val faceState = FaceState(
            leftEyeOpen = leftEyeOpen,
            rightEyeOpen = rightEyeOpen,
            isSmiling = isSmiling,
            headRotationY = headRotationY,
            headRotationX = headRotationX,
            blendShapes = null // Skip blendshapes array for now
        )
        
        if (isSmiling) {
            detectedGestures.add(CameraSwitchFacialGesture.SMILE)
            android.util.Log.d("FaceProcessingService", "Smile detected")
        }
        
        // Eye gesture detection - check in priority order
        if (!leftEyeOpen && !rightEyeOpen) {
            // Both eyes closed = blink (highest priority)
            detectedGestures.add(CameraSwitchFacialGesture.BLINK)
            android.util.Log.d("FaceProcessingService", "Blink detected")
        } else if (!leftEyeOpen && rightEyeOpen) {
            // Only left eye closed = left wink
            detectedGestures.add(CameraSwitchFacialGesture.LEFT_WINK)
            android.util.Log.d("FaceProcessingService", "Left wink detected")
        } else if (leftEyeOpen && !rightEyeOpen) {
            // Only right eye closed = right wink
            detectedGestures.add(CameraSwitchFacialGesture.RIGHT_WINK)
            android.util.Log.d("FaceProcessingService", "Right wink detected")
        }
        
        val leftThreshold = getHeadTurnLeftThreshold()
        val rightThreshold = getHeadTurnRightThreshold()
        val upThreshold = getHeadTurnUpThreshold()
        val downThreshold = getHeadTurnDownThreshold()
        
        if (headRotationY > leftThreshold) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_LEFT)
        }
        
        if (headRotationY < -rightThreshold) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_RIGHT)
        }
        
        if (headRotationX > upThreshold) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_UP)
        }
        
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
    
    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }
}