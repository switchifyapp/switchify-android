package com.enaboapps.switchify.service.face

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.face.detection.GestureDetector
import com.enaboapps.switchify.service.face.processing.BlendshapeProcessor
import com.enaboapps.switchify.service.face.processing.MediaPipeManager
import com.enaboapps.switchify.service.face.state.FaceStateManager
import com.enaboapps.switchify.service.face.utils.HeadPoseCalculator
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture

/**
 * Modular face processing service using class-based architecture.
 * Preserves all original functionality while improving maintainability.
 */
class FaceProcessingService(context: Context) {
    private val appContext: Context = context.applicationContext
    private val preferenceManager = PreferenceManager(context)

    // Background processing
    private val processingThread = HandlerThread("FaceProcessing").apply { start() }
    private val processingHandler = Handler(processingThread.looper)

    @Volatile
    private var isCleanedUp = false

    // Modular components
    private val mediaPipeManager = MediaPipeManager()
    private val blendshapeProcessor = BlendshapeProcessor()
    private val faceStateManager = FaceStateManager()
    private val headPoseCalculator = HeadPoseCalculator(appContext)
    private val gestureDetector = GestureDetector(preferenceManager)

    companion object {
        private const val TAG = "FaceProcessingService"

        /**
         * Convert sensitivity level (1-10) to rotation threshold in degrees.
         */
        fun getHeadTurnThreshold(sensitivity: Int): Float {
            return when (sensitivity.coerceIn(1, 10)) {
                1 -> 5.0f   // Very high sensitivity
                2 -> 8.0f
                3 -> 12.0f
                4 -> 15.0f
                5 -> 18.0f  // Default
                6 -> 22.0f
                7 -> 25.0f
                8 -> 28.0f
                9 -> 32.0f
                10 -> 35.0f // Very low sensitivity
                else -> 18.0f
            }
        }
    }

    /**
     * Data class representing detected face gestures - preserved exactly as original
     */
    data class FaceDetectionResult(
        val detectedGestures: Set<String>,
        val faceState: FaceState,
        val blendshapeScores: BlendshapeScores = BlendshapeScores(),
        val gestureConfidence: Map<String, Float> = emptyMap()
    )

    /**
     * Data class containing real-time blendshape scores for UI feedback - preserved exactly as original
     */
    data class BlendshapeScores(
        val smileScore: Float = 0f,
        val leftEyeCloseScore: Float = 0f,
        val rightEyeCloseScore: Float = 0f,
        val blinkScore: Float = 0f,
        val mouthCloseScore: Float = 1f, // Default to closed (1.0)
        val puckerScore: Float = 0f
    )

    /**
     * Data class representing the raw face state for comparison - preserved exactly as original
     */
    data class FaceState(
        val leftEyeOpen: Boolean = true,
        val rightEyeOpen: Boolean = true,
        val isSmiling: Boolean = false,
        val headRotationY: Float = 0f,
        val headRotationX: Float = 0f,
        val blendShapes: FloatArray? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FaceState

            if (leftEyeOpen != other.leftEyeOpen) return false
            if (rightEyeOpen != other.rightEyeOpen) return false
            if (isSmiling != other.isSmiling) return false
            if (headRotationY != other.headRotationY) return false
            if (headRotationX != other.headRotationX) return false
            if (blendShapes != null || other.blendShapes != null) {
                if (blendShapes == null || other.blendShapes == null) return false
                if (!blendShapes.contentEquals(other.blendShapes)) return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = leftEyeOpen.hashCode()
            result = 31 * result + rightEyeOpen.hashCode()
            result = 31 * result + isSmiling.hashCode()
            result = 31 * result + headRotationY.hashCode()
            result = 31 * result + headRotationX.hashCode()
            result = 31 * result + (blendShapes?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Configure camera orientation for coordinate system normalization
     * @param rotation Device rotation (Surface.ROTATION_0, ROTATION_90, etc.)
     * @param frontCamera Whether using front-facing camera (default: true)
     */
    fun setCameraOrientation(rotation: Int, frontCamera: Boolean = true) {
        headPoseCalculator.setCameraOrientation(rotation, frontCamera)
    }

    /**
     * Get current coordinate system information
     */
    fun getCoordinateSystemInfo(): String {
        val coordinateSystem = headPoseCalculator.getCoordinateSystemInfo()
        return "Device: ${coordinateSystem.deviceKey}\n" +
                "Pitch Inverted: ${coordinateSystem.shouldApplyPitchInversion()}\n" +
                "Yaw Inverted: ${coordinateSystem.shouldApplyYawInversion()}\n" +
                "Confidence: ${"%.0f".format(coordinateSystem.confidence * 100)}%"
    }

    /**
     * Clear cached coordinate system (for testing)
     */
    fun clearCoordinateSystemCache() {
        headPoseCalculator.clearCoordinateSystemCache()
    }

    /**
     * Set custom coordinate system for testing
     */
    fun setCustomCoordinateSystem(pitchInverted: Boolean, yawInverted: Boolean) {
        headPoseCalculator.setCustomCoordinateSystem(pitchInverted, yawInverted)
    }

    /**
     * Main face processing method - preserved exact interface as original
     */
    fun processFace(
        bitmap: Bitmap,
        timestampMs: Long = System.currentTimeMillis(),
        callback: (FaceDetectionResult?) -> Unit
    ) {
        if (isCleanedUp) {
            callback(null)
            return
        }

        processingHandler.post {
            if (isCleanedUp) {
                callback(null)
                return@post
            }

            // Ensure MediaPipe is initialized
            if (!mediaPipeManager.ensureFaceLandmarker(appContext)) {
                callback(null)
                return@post
            }

            try {
                val result = mediaPipeManager.detectForVideo(bitmap, timestampMs)
                val detectionResult = processLandmarkerResult(result, timestampMs)
                callback(detectionResult)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing face", e)
                callback(null)
            }
        }
    }

    private fun processLandmarkerResult(
        result: com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult?,
        timestampMs: Long
    ): FaceDetectionResult {
        if (result == null || result.faceLandmarks().isEmpty()) {
            return FaceDetectionResult(emptySet(), FaceState(), BlendshapeScores())
        }

        val gestureConfidence = mutableMapOf<String, Float>()

        // Process blendshapes using the modular processor
        val blendshapeScores =
            if (result.faceBlendshapes().isPresent && result.faceBlendshapes().get().isNotEmpty()) {
                val processedScores =
                    blendshapeProcessor.processBlendshapes(result.faceBlendshapes().get()[0])

                // Convert to original format for compatibility
                BlendshapeScores(
                    smileScore = processedScores.smileScore,
                    leftEyeCloseScore = processedScores.leftEyeCloseScore,
                    rightEyeCloseScore = processedScores.rightEyeCloseScore,
                    blinkScore = processedScores.blinkScore,
                    mouthCloseScore = processedScores.mouthCloseScore,
                    puckerScore = processedScores.puckerScore
                )
            } else {
                BlendshapeScores()
            }

        // Process head pose using the modular calculator with face landmarks for coordinate detection
        val faceLandmarks =
            if (result.faceLandmarks().isNotEmpty()) result.faceLandmarks()[0] else null
        val headPose =
            if (result.facialTransformationMatrixes().isPresent && result.facialTransformationMatrixes()
                    .get().isNotEmpty()
            ) {
                headPoseCalculator.extractEulerAngles(
                    result.facialTransformationMatrixes().get()[0], faceLandmarks
                )
            } else {
                HeadPoseCalculator.EulerAngles(0f, 0f, 0f)
            }

        // Process face state using the state manager
        val faceState = faceStateManager.processFaceState(
            leftEyeClose = blendshapeScores.leftEyeCloseScore,
            rightEyeClose = blendshapeScores.rightEyeCloseScore,
            smoothedSmileScore = blendshapeScores.smileScore,
            smoothedBlinkScore = blendshapeScores.blinkScore,
            headYaw = headPose.yaw,
            headPitch = headPose.pitch,
            currentTime = timestampMs
        )

        // Process wink detection
        val winkResults = faceStateManager.processWinkDetection(
            leftEyeClose = blendshapeScores.leftEyeCloseScore,
            rightEyeClose = blendshapeScores.rightEyeCloseScore,
            currentTime = timestampMs
        )

        // Convert face state manager result to original format for compatibility
        val compatibleFaceState = FaceState(
            leftEyeOpen = faceState.leftEyeOpen,
            rightEyeOpen = faceState.rightEyeOpen,
            isSmiling = faceState.isSmiling,
            headRotationY = faceState.headRotationY,
            headRotationX = faceState.headRotationX,
            blendShapes = faceState.blendShapes
        )

        // Detect gestures using the gesture detector
        val detectedGestures = gestureDetector.detectGestures(
            blendshapeScores = blendshapeProcessor.processBlendshapes(
                result.faceBlendshapes().get()[0]
            ),
            faceState = faceState,
            winkResults = winkResults,
            timestampMs = timestampMs
        )

        if (BuildConfig.DEBUG && detectedGestures.isNotEmpty()) {
            Log.d(TAG, "Detected gestures: ${detectedGestures.joinToString(", ")}")
        }

        return FaceDetectionResult(
            detectedGestures = detectedGestures,
            faceState = compatibleFaceState,
            blendshapeScores = blendshapeScores,
            gestureConfidence = gestureConfidence
        )
    }

    /**
     * Get required hold time for gesture - preserved exactly as original
     */
    fun getGestureTime(gestureId: String): Long {
        return when (gestureId) {
            CameraSwitchFacialGesture.SMILE ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_SMILE_TIME,
                    500L
                )

            CameraSwitchFacialGesture.LEFT_WINK ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_LEFT_WINK_TIME,
                    300L
                )

            CameraSwitchFacialGesture.RIGHT_WINK ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_RIGHT_WINK_TIME,
                    300L
                )

            CameraSwitchFacialGesture.BLINK ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_BLINK_TIME,
                    400L
                )

            CameraSwitchFacialGesture.PUCKER ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_PUCKER_TIME,
                    500L
                )

            CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            CameraSwitchFacialGesture.HEAD_TURN_UP,
            CameraSwitchFacialGesture.HEAD_TURN_DOWN -> 0L // No hold time for head turns
            else -> 500L // Default
        }
    }

    fun close() {
        isCleanedUp = true
        processingHandler.removeCallbacksAndMessages(null)

        // Clean up modular components
        mediaPipeManager.close()
        blendshapeProcessor.reset()
        faceStateManager.reset()
        headPoseCalculator.reset()

        processingThread.quitSafely()
    }
}