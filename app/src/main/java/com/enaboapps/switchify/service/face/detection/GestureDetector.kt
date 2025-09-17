package com.enaboapps.switchify.service.face.detection

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.face.processing.BlendshapeProcessor
import com.enaboapps.switchify.service.face.state.FaceStateManager
import com.enaboapps.switchify.service.face.utils.HeadPoseCalculator
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import kotlin.math.abs

/**
 * Coordinates gesture detection from face processing results
 */
class GestureDetector(private val preferenceManager: PreferenceManager) {

    companion object {
        // Mouth open detection threshold
        const val MOUTH_CLOSE_MAX_THRESHOLD = 0.8f

        // Head pose limits for gesture gating
        const val HEAD_POSE_YAW_LIMIT = 45f
        const val HEAD_POSE_PITCH_LIMIT = 30f
    }

    /**
     * Detect gestures from processed face data
     */
    fun detectGestures(
        blendshapeScores: BlendshapeProcessor.BlendshapeScores,
        faceState: FaceStateManager.FaceState,
        winkResults: Pair<Boolean, Boolean>,
        timestampMs: Long
    ): Set<String> {
        val detectedGestures = mutableSetOf<String>()
        val gestureConfidence = mutableMapOf<String, Float>()

        // Check if head pose allows for reliable eye gesture detection
        val validHeadPose = abs(faceState.headRotationY) < HEAD_POSE_YAW_LIMIT &&
                           abs(faceState.headRotationX) < HEAD_POSE_PITCH_LIMIT

        // Smile detection
        if (faceState.isSmiling) {
            detectedGestures.add(CameraSwitchFacialGesture.SMILE)
            gestureConfidence[CameraSwitchFacialGesture.SMILE] = blendshapeScores.smileScore
        }

        // Eye-based gestures only if head pose is valid
        if (validHeadPose) {
            // Blink detection
            if (!faceState.leftEyeOpen && !faceState.rightEyeOpen) {
                detectedGestures.add(CameraSwitchFacialGesture.BLINK)
                gestureConfidence[CameraSwitchFacialGesture.BLINK] = blendshapeScores.blinkScore
            }

            // Wink detection
            val (leftWink, rightWink) = winkResults
            if (leftWink) {
                detectedGestures.add(CameraSwitchFacialGesture.LEFT_WINK)
                gestureConfidence[CameraSwitchFacialGesture.LEFT_WINK] = blendshapeScores.leftEyeCloseScore
            }
            if (rightWink) {
                detectedGestures.add(CameraSwitchFacialGesture.RIGHT_WINK)
                gestureConfidence[CameraSwitchFacialGesture.RIGHT_WINK] = blendshapeScores.rightEyeCloseScore
            }

            // Note: Mouth open detection removed as MOUTH_OPEN gesture doesn't exist in constants
        }

        // Head turn detection
        detectHeadTurns(faceState.headRotationY, faceState.headRotationX, detectedGestures, gestureConfidence)

        return detectedGestures
    }

    private fun detectHeadTurns(
        yaw: Float,
        pitch: Float,
        detectedGestures: MutableSet<String>,
        gestureConfidence: MutableMap<String, Float>
    ) {
        val leftThreshold = getHeadTurnLeftThreshold()
        val rightThreshold = getHeadTurnRightThreshold()
        val upThreshold = getHeadTurnUpThreshold()
        val downThreshold = getHeadTurnDownThreshold()

        // Calculate confidence scores for each direction
        val leftTurnMagnitude = if (yaw < -leftThreshold) abs(yaw + leftThreshold) else 0f
        val rightTurnMagnitude = if (yaw > rightThreshold) abs(yaw - rightThreshold) else 0f
        val upTurnMagnitude = if (pitch < -upThreshold) abs(pitch + upThreshold) else 0f
        val downTurnMagnitude = if (pitch > downThreshold) abs(pitch - downThreshold) else 0f

        // Only trigger the strongest head turn to avoid conflicts
        val maxMagnitude = maxOf(leftTurnMagnitude, rightTurnMagnitude, upTurnMagnitude, downTurnMagnitude)

        when (maxMagnitude) {
            leftTurnMagnitude -> if (leftTurnMagnitude > 0f) {
                detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_LEFT)
                gestureConfidence[CameraSwitchFacialGesture.HEAD_TURN_LEFT] = normalizeConfidence(leftTurnMagnitude, leftThreshold, 15f)
            }
            rightTurnMagnitude -> if (rightTurnMagnitude > 0f) {
                detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_RIGHT)
                gestureConfidence[CameraSwitchFacialGesture.HEAD_TURN_RIGHT] = normalizeConfidence(rightTurnMagnitude, rightThreshold, 15f)
            }
            upTurnMagnitude -> if (upTurnMagnitude > 0f) {
                detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_UP)
                gestureConfidence[CameraSwitchFacialGesture.HEAD_TURN_UP] = normalizeConfidence(upTurnMagnitude, upThreshold, 15f)
            }
            downTurnMagnitude -> if (downTurnMagnitude > 0f) {
                detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_DOWN)
                gestureConfidence[CameraSwitchFacialGesture.HEAD_TURN_DOWN] = normalizeConfidence(downTurnMagnitude, downThreshold, 15f)
            }
        }
    }

    private fun normalizeConfidence(value: Float, base: Float, delta: Float): Float {
        return (value / (base + delta)).coerceIn(0f, 1f)
    }

    private fun getHeadTurnLeftThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_LEFT_SENSITIVITY, 4
        )
        return convertSensitivityToThreshold(sensitivity)
    }

    private fun getHeadTurnRightThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_RIGHT_SENSITIVITY, 4
        )
        return convertSensitivityToThreshold(sensitivity)
    }

    private fun getHeadTurnUpThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_UP_SENSITIVITY, 4
        )
        return convertSensitivityToThreshold(sensitivity)
    }

    private fun getHeadTurnDownThreshold(): Float {
        val sensitivity = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_HEAD_TURN_DOWN_SENSITIVITY, 4
        )
        return convertSensitivityToThreshold(sensitivity)
    }

    private fun convertSensitivityToThreshold(sensitivity: Int): Float {
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