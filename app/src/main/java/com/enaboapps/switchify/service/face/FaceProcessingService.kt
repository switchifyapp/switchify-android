package com.enaboapps.switchify.service.face

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Centralized face processing service that handles gesture detection logic.
 * Used by both CameraSwitchManager and CameraSettingsScreenModel to ensure consistency.
 * Enhanced with MediaPipe support for improved accuracy.
 */
class FaceProcessingService(context: Context) {
    private val appContext: Context = context.applicationContext

    private val preferenceManager = PreferenceManager(context)
    private var faceLandmarker: FaceLandmarker? = null

    // Background processing
    private val processingThread = HandlerThread("FaceProcessing").apply { start() }
    private val processingHandler = Handler(processingThread.looper)

    @Volatile
    private var isCleanedUp = false

    // Cached blendshape indices for performance
    private var blendshapeIndices: BlendshapeIndices? = null

    // Smoothing and hysteresis state
    private var smoothedYaw = 0f
    private var smoothedPitch = 0f
    private var smoothedBlinkScore = 0f
    private var smoothedSmileScore = 0f

    // Hysteresis state
    private var isBlinkActive = false
    private var isSmileActive = false
    private var lastBlinkTime = 0L

    companion object {
        private const val TAG = "FaceProcessingService"

        // Hysteresis thresholds (enter/exit)
        const val SMILE_ENTER_THRESHOLD = 0.35f
        const val SMILE_EXIT_THRESHOLD = 0.25f
        const val BLINK_ENTER_THRESHOLD = 0.55f
        const val BLINK_EXIT_THRESHOLD = 0.45f

        // Smoothing factor for EMA (0.0 = no smoothing, 1.0 = max smoothing)
        const val EMA_ALPHA = 0.3f

        // Refractory period for blink detection (ms)
        const val BLINK_REFRACTORY_PERIOD = 200L

        // Correct MediaPipe Face Landmarker 468-point model indices
        const val NOSE_TIP_INDEX = 1
        const val CHIN_INDEX = 152
        const val LEFT_EYE_OUTER_INDEX = 33
        const val RIGHT_EYE_OUTER_INDEX = 263

        /**
         * Convert sensitivity level (1-10) to rotation threshold in degrees.
         * Higher sensitivity = lower threshold (easier to trigger)
         * Lower sensitivity = higher threshold (harder to trigger)
         */
        fun getHeadTurnThreshold(sensitivity: Int): Float {
            return when (sensitivity.coerceIn(1, 10)) {
                1 -> 5.0f   // Very high sensitivity (easy to trigger)
                2 -> 8.0f
                3 -> 12.0f
                4 -> 16.0f
                5 -> 20.0f  // Default
                6 -> 24.0f
                7 -> 28.0f
                8 -> 32.0f
                9 -> 36.0f
                10 -> 40.0f  // Very low sensitivity (hard to trigger)
                else -> 20.0f
            }
        }
    }

    /**
     * Cached blendshape indices for performance optimization
     */
    private data class BlendshapeIndices(
        val eyeBlinkLeft: Int,
        val eyeBlinkRight: Int,
        val eyeSquintLeft: Int,
        val eyeSquintRight: Int,
        val mouthSmileLeft: Int,
        val mouthSmileRight: Int
    )

    private fun ensureFaceLandmarker(context: Context): Boolean {
        if (faceLandmarker != null) return true
        return try {
            initFaceLandmarker(context)
            faceLandmarker != null
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize MediaPipe FaceLandmarker (fatal)", t)
            faceLandmarker = null
            false
        }
    }

    private fun initFaceLandmarker(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)  // Changed from LIVE_STREAM to avoid listener requirement
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)  // Enable transformation matrices
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.3f)
                .setMinFacePresenceConfidence(0.3f)
                .setMinTrackingConfidence(0.3f)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe FaceLandmarker", e)
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
            if (!blendShapes.contentEquals(other.blendShapes)) return false

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
     * Processes a face bitmap in the background and calls back with detected gestures
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

            if (faceLandmarker == null) {
                ensureFaceLandmarker(appContext)
            }
            val landmarker = faceLandmarker
            if (landmarker == null) {
                callback(null)
                return@post
            }

            try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                val result = landmarker.detectForVideo(mpImage, timestampMs)
                val detectionResult = processLandmarkerResult(result)
                callback(detectionResult)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing face", e)
                callback(null)
            }
        }
    }

    private fun processLandmarkerResult(result: com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult): FaceDetectionResult {
        val detectedGestures = mutableSetOf<String>()

        if (result.faceLandmarks().isEmpty()) {
            return FaceDetectionResult(emptySet(), FaceState())
        }

        // Extract face data
        var leftEyeOpen = true
        var rightEyeOpen = true
        var isSmiling = false
        var headRotationX = 0f
        var headRotationY = 0f
        val currentTime = System.currentTimeMillis()

        // Process head pose using transformation matrices if available
        try {
            if (result.facialTransformationMatrixes().isPresent && result.facialTransformationMatrixes()
                    .get().isNotEmpty()
            ) {
                val transformMatrix = result.facialTransformationMatrixes().get()[0]
                val eulerAngles = extractEulerAnglesFromMatrix(transformMatrix)

                // Apply EMA smoothing
                smoothedYaw = applyEMA(smoothedYaw, eulerAngles.yaw, EMA_ALPHA)
                smoothedPitch = applyEMA(smoothedPitch, eulerAngles.pitch, EMA_ALPHA)

                headRotationY = smoothedYaw
                headRotationX = smoothedPitch

                Log.v(TAG, "Head pose: yaw=${headRotationY}°, pitch=${headRotationX}°")
            } else {
                // Fallback to landmark-based head pose estimation with corrected indices
                val landmarks = result.faceLandmarks()[0]
                if (landmarks.size > maxOf(
                        NOSE_TIP_INDEX,
                        CHIN_INDEX,
                        LEFT_EYE_OUTER_INDEX,
                        RIGHT_EYE_OUTER_INDEX
                    )
                ) {
                    val noseTip = landmarks[NOSE_TIP_INDEX]
                    val leftEyeCorner = landmarks[LEFT_EYE_OUTER_INDEX]
                    val rightEyeCorner = landmarks[RIGHT_EYE_OUTER_INDEX]  // Corrected index
                    val chin = landmarks[CHIN_INDEX]  // Corrected index

                    // Calculate head rotation Y (left/right turn) using eye positions relative to nose
                    val eyeCenterX = (leftEyeCorner.x() + rightEyeCorner.x()) / 2f
                    val noseX = noseTip.x()
                    val eyeDistance = abs(rightEyeCorner.x() - leftEyeCorner.x())

                    if (eyeDistance > 0) {
                        val rawYaw = ((noseX - eyeCenterX) / eyeDistance) * 45f
                        smoothedYaw = applyEMA(smoothedYaw, rawYaw, EMA_ALPHA)
                        headRotationY = smoothedYaw
                    }

                    // Calculate head rotation X (up/down) using nose to chin distance
                    val noseY = noseTip.y()
                    val chinY = chin.y()
                    val eyeCenterY = (leftEyeCorner.y() + rightEyeCorner.y()) / 2f
                    val faceHeight = abs(chinY - eyeCenterY)

                    if (faceHeight > 0) {
                        val noseTiltRatio = (noseY - eyeCenterY) / faceHeight
                        val rawPitch = noseTiltRatio * 30f
                        smoothedPitch = applyEMA(smoothedPitch, rawPitch, EMA_ALPHA)
                        headRotationX = smoothedPitch
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating head pose", e)
        }

        // Process blendshapes with improved robustness and caching
        try {
            if (result.faceBlendshapes().isPresent && result.faceBlendshapes().get().isNotEmpty()) {
                val blendShapes = result.faceBlendshapes().get()[0]

                // Cache blendshape indices on first run for performance
                if (blendshapeIndices == null) {
                    blendshapeIndices = cacheBlendshapeIndices(blendShapes)
                }

                val indices = blendshapeIndices
                if (indices != null) {
                    // Extract blendshape scores by index (faster than string lookup)
                    val leftEyeBlink =
                        if (indices.eyeBlinkLeft >= 0) blendShapes[indices.eyeBlinkLeft].score() else 0f
                    val rightEyeBlink =
                        if (indices.eyeBlinkRight >= 0) blendShapes[indices.eyeBlinkRight].score() else 0f
                    val leftEyeSquint =
                        if (indices.eyeSquintLeft >= 0) blendShapes[indices.eyeSquintLeft].score() else 0f
                    val rightEyeSquint =
                        if (indices.eyeSquintRight >= 0) blendShapes[indices.eyeSquintRight].score() else 0f
                    val mouthSmileLeft =
                        if (indices.mouthSmileLeft >= 0) blendShapes[indices.mouthSmileLeft].score() else 0f
                    val mouthSmileRight =
                        if (indices.mouthSmileRight >= 0) blendShapes[indices.mouthSmileRight].score() else 0f

                    // Combine blink cues for robustness: max of blink and squint for each eye
                    val leftEyeCloseScore = max(leftEyeBlink, leftEyeSquint)
                    val rightEyeCloseScore = max(rightEyeBlink, rightEyeSquint)
                    val combinedBlinkScore = max(leftEyeCloseScore, rightEyeCloseScore)
                    val smileScore = (mouthSmileLeft + mouthSmileRight) / 2f

                    // Apply EMA smoothing to scores
                    smoothedBlinkScore = applyEMA(smoothedBlinkScore, combinedBlinkScore, EMA_ALPHA)
                    smoothedSmileScore = applyEMA(smoothedSmileScore, smileScore, EMA_ALPHA)

                    // Apply hysteresis for blink detection
                    if (!isBlinkActive && smoothedBlinkScore > BLINK_ENTER_THRESHOLD) {
                        // Check refractory period
                        if (currentTime - lastBlinkTime > BLINK_REFRACTORY_PERIOD) {
                            isBlinkActive = true
                        }
                    } else if (isBlinkActive && smoothedBlinkScore < BLINK_EXIT_THRESHOLD) {
                        isBlinkActive = false
                        lastBlinkTime = currentTime
                    }

                    // Apply hysteresis for smile detection
                    if (!isSmileActive && smoothedSmileScore > SMILE_ENTER_THRESHOLD) {
                        isSmileActive = true
                    } else if (isSmileActive && smoothedSmileScore < SMILE_EXIT_THRESHOLD) {
                        isSmileActive = false
                    }

                    // Determine individual eye states for wink detection
                    leftEyeOpen = leftEyeCloseScore < BLINK_ENTER_THRESHOLD
                    rightEyeOpen = rightEyeCloseScore < BLINK_ENTER_THRESHOLD
                    isSmiling = isSmileActive
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing blendshapes", e)
        }

        val faceState = FaceState(
            leftEyeOpen = leftEyeOpen,
            rightEyeOpen = rightEyeOpen,
            isSmiling = isSmiling,
            headRotationY = headRotationY,
            headRotationX = headRotationX,
            blendShapes = null // Skip blendshapes array for now - can be added for debugging
        )

        // Gesture detection with improved logic
        if (isSmiling) {
            detectedGestures.add(CameraSwitchFacialGesture.SMILE)
            Log.d(TAG, "Smile detected (smoothed score: $smoothedSmileScore)")
        }

        // Eye gesture detection - check in priority order, using hysteresis-controlled blink state
        if (isBlinkActive) {
            if (!leftEyeOpen && !rightEyeOpen) {
                // Both eyes closed = blink (highest priority)
                detectedGestures.add(CameraSwitchFacialGesture.BLINK)
                Log.d(TAG, "Blink detected (smoothed score: $smoothedBlinkScore)")
            } else if (!leftEyeOpen) {
                // Only left eye closed = left wink
                detectedGestures.add(CameraSwitchFacialGesture.LEFT_WINK)
                Log.d(TAG, "Left wink detected")
            } else if (!rightEyeOpen) {
                // Only right eye closed = right wink
                detectedGestures.add(CameraSwitchFacialGesture.RIGHT_WINK)
                Log.d(TAG, "Right wink detected")
            }
        }

        // Head turn detection with corrected sign logic
        val leftThreshold = getHeadTurnLeftThreshold()
        val rightThreshold = getHeadTurnRightThreshold()
        val upThreshold = getHeadTurnUpThreshold()
        val downThreshold = getHeadTurnDownThreshold()

        // Calculate the largest head turn movement to avoid multiple simultaneous detections
        val headTurnMagnitudes = mutableMapOf<String, Float>()

        // Verify sign conventions: positive yaw = right turn, negative yaw = left turn
        // Adjust signs based on your specific coordinate system if needed
        if (headRotationY > rightThreshold) {  // Positive yaw = right turn
            headTurnMagnitudes[CameraSwitchFacialGesture.HEAD_TURN_RIGHT] =
                headRotationY - rightThreshold
        }

        if (headRotationY < -leftThreshold) {  // Negative yaw = left turn
            headTurnMagnitudes[CameraSwitchFacialGesture.HEAD_TURN_LEFT] =
                abs(headRotationY + leftThreshold)
        }

        // Verify sign conventions: positive pitch = down, negative pitch = up
        // Adjust signs based on your specific coordinate system if needed
        if (headRotationX > downThreshold) {  // Positive pitch = down
            headTurnMagnitudes[CameraSwitchFacialGesture.HEAD_TURN_DOWN] =
                headRotationX - downThreshold
        }

        if (headRotationX < -upThreshold) {  // Negative pitch = up
            headTurnMagnitudes[CameraSwitchFacialGesture.HEAD_TURN_UP] =
                abs(headRotationX + upThreshold)
        }

        // Only add the gesture with the largest magnitude
        if (headTurnMagnitudes.isNotEmpty()) {
            val largestTurn = headTurnMagnitudes.maxByOrNull { it.value }
            if (largestTurn != null) {
                detectedGestures.add(largestTurn.key)
                Log.d(
                    TAG,
                    "Head turn detected: ${largestTurn.key} (magnitude: ${largestTurn.value}, yaw: $headRotationY°, pitch: $headRotationX°)"
                )
            }
        }

        return FaceDetectionResult(detectedGestures, faceState)
    }

    /**
     * Gets the required hold time for a specific gesture from preferences
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

            CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            CameraSwitchFacialGesture.HEAD_TURN_UP,
            CameraSwitchFacialGesture.HEAD_TURN_DOWN -> 0L // Instant trigger, no hold time
            else -> 500L // Default fallback
        }
    }

    // Head turn threshold getters using global preferences
    private fun getHeadTurnLeftThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_LEFT_SENSITIVITY,
            4
        )
        return convertSensitivityToThreshold(sensitivity)
    }

    private fun getHeadTurnRightThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_RIGHT_SENSITIVITY,
            4
        )
        return convertSensitivityToThreshold(sensitivity)
    }

    private fun getHeadTurnUpThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_UP_SENSITIVITY,
            4
        )
        return convertSensitivityToThreshold(sensitivity)
    }

    private fun getHeadTurnDownThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_DOWN_SENSITIVITY,
            4
        )
        return convertSensitivityToThreshold(sensitivity)
    }

    /**
     * Convert sensitivity level (1-8) to rotation threshold in degrees.
     */
    private fun convertSensitivityToThreshold(sensitivity: Int): Float {
        return getHeadTurnThreshold(sensitivity)
    }

    // Utility methods for smoothing and matrix processing

    /**
     * Caches blendshape indices for performance optimization
     */
    private fun cacheBlendshapeIndices(blendShapes: List<com.google.mediapipe.tasks.components.containers.Category>): BlendshapeIndices {
        var eyeBlinkLeft = -1
        var eyeBlinkRight = -1
        var eyeSquintLeft = -1
        var eyeSquintRight = -1
        var mouthSmileLeft = -1
        var mouthSmileRight = -1

        for (i in 0 until blendShapes.size) {
            when (blendShapes[i].categoryName()) {
                "eyeBlinkLeft" -> eyeBlinkLeft = i
                "eyeBlinkRight" -> eyeBlinkRight = i
                "eyeSquintLeft" -> eyeSquintLeft = i
                "eyeSquintRight" -> eyeSquintRight = i
                "mouthSmileLeft" -> mouthSmileLeft = i
                "mouthSmileRight" -> mouthSmileRight = i
            }
        }

        return BlendshapeIndices(
            eyeBlinkLeft = eyeBlinkLeft,
            eyeBlinkRight = eyeBlinkRight,
            eyeSquintLeft = eyeSquintLeft,
            eyeSquintRight = eyeSquintRight,
            mouthSmileLeft = mouthSmileLeft,
            mouthSmileRight = mouthSmileRight
        )
    }

    /**
     * Applies exponential moving average for smoothing
     */
    private fun applyEMA(previousValue: Float, newValue: Float, alpha: Float): Float {
        return alpha * newValue + (1f - alpha) * previousValue
    }

    /**
     * Data class for Euler angles extracted from transformation matrix
     */
    private data class EulerAngles(
        val yaw: Float,    // Rotation around Y-axis (left/right)
        val pitch: Float,  // Rotation around X-axis (up/down)
        val roll: Float    // Rotation around Z-axis (tilt)
    )

    /**
     * Extracts Euler angles from MediaPipe's transformation matrix
     * The exact structure depends on the MediaPipe version and matrix format
     */
    private fun extractEulerAnglesFromMatrix(transformMatrix: Any): EulerAngles {
        return try {
            // Try to access matrix data - the actual structure may vary
            val matrixData = when (transformMatrix) {
                is FloatArray -> {
                    // Matrix is directly provided as FloatArray
                    transformMatrix
                }

                else -> {
                    // Try to access matrix data from object
                    when {
                        transformMatrix::class.java.name.contains("Matrix") -> {
                            // Try common matrix data access patterns
                            val dataMethod = transformMatrix::class.java.methods.find {
                                it.name == "data" || it.name == "getData" || it.name == "toFloatArray"
                            }
                            if (dataMethod != null) {
                                dataMethod.invoke(transformMatrix) as? FloatArray
                            } else {
                                // Try direct field access
                                val dataField = transformMatrix::class.java.fields.find {
                                    it.name == "data" || it.name == "matrix"
                                }
                                dataField?.get(transformMatrix) as? FloatArray
                            }
                        }

                        else -> {
                            Log.w(TAG, "Unknown matrix type: ${transformMatrix::class.java}")
                            null
                        }
                    }
                }
            }

            if (matrixData != null && matrixData.size >= 9) {
                extractEulerFromFloatArray(matrixData)
            } else {
                Log.w(TAG, "Could not extract matrix data or insufficient size")
                EulerAngles(0f, 0f, 0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting matrix data", e)
            EulerAngles(0f, 0f, 0f)
        }
    }

    /**
     * Extracts Euler angles from float array representing rotation matrix
     * Handles 4x4 transformation matrix in row-major format as per MediaPipe documentation
     */
    private fun extractEulerFromFloatArray(matrixData: FloatArray): EulerAngles {
        // Handle different matrix sizes and formats
        val r00: Float
        val r10: Float
        val r20: Float
        val r01: Float
        val r11: Float
        val r21: Float
        val r02: Float
        val r12: Float
        val r22: Float

        when {
            matrixData.size >= 16 -> {
                // 4x4 matrix in row-major order - extract 3x3 rotation matrix R
                // MediaPipe transformation matrix format: [R t; 0 1] where R is 3x3 rotation
                r00 = matrixData[0]   // Row 0, Col 0
                r01 = matrixData[1]   // Row 0, Col 1  
                r02 = matrixData[2]   // Row 0, Col 2
                r10 = matrixData[4]   // Row 1, Col 0
                r11 = matrixData[5]   // Row 1, Col 1
                r12 = matrixData[6]   // Row 1, Col 2
                r20 = matrixData[8]   // Row 2, Col 0
                r21 = matrixData[9]   // Row 2, Col 1
                r22 = matrixData[10]  // Row 2, Col 2
            }

            matrixData.size >= 9 -> {
                // 3x3 matrix in row-major order
                r00 = matrixData[0]
                r01 = matrixData[1]
                r02 = matrixData[2]
                r10 = matrixData[3]
                r11 = matrixData[4]
                r12 = matrixData[5]
                r20 = matrixData[6]
                r21 = matrixData[7]
                r22 = matrixData[8]
            }

            else -> {
                Log.w(TAG, "Matrix data too small: ${matrixData.size}")
                return EulerAngles(0f, 0f, 0f)
            }
        }

        // Convert rotation matrix to Euler angles (YXZ convention for face pose)
        // This convention is commonly used for face tracking: Yaw (Y), Pitch (X), Roll (Z)
        val sy = sqrt(r00 * r00 + r01 * r01)

        val singular = sy < 1e-6

        val yaw: Float
        val pitch: Float
        val roll: Float

        if (!singular) {
            // Standard rotation: extract yaw (left/right), pitch (up/down), roll (tilt)
            yaw = atan2(
                r02,
                r22
            ) * 180f / PI.toFloat()      // Rotation around Y-axis (left/right) - fixed sign
            pitch = atan2(r12, sy) * 180f / PI.toFloat()       // Rotation around X-axis (up/down)  
            roll = atan2(-r10, r00) * 180f / PI.toFloat()      // Rotation around Z-axis (tilt)
        } else {
            // Handle gimbal lock case
            yaw = atan2(r02, r22) * 180f / PI.toFloat()      // Fixed sign for consistency
            pitch = atan2(r12, sy) * 180f / PI.toFloat()
            roll = 0f
        }

        return EulerAngles(yaw, pitch, roll)
    }

    fun close() {
        isCleanedUp = true
        processingHandler.removeCallbacksAndMessages(null)
        faceLandmarker?.close()
        faceLandmarker = null
        processingThread.quitSafely()
    }
}
