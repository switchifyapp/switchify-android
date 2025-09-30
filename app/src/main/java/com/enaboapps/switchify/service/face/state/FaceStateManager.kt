package com.enaboapps.switchify.service.face.state

/**
 * Manages face detection state including baseline calibration and hysteresis
 */
class FaceStateManager {
    // Baseline calibration state
    private var baselineLeftEyeClose = 0f
    private var baselineRightEyeClose = 0f
    private var baselineSmile = 0f
    private var baselineReadyEyes = false
    private var baselineReadySmile = false

    // Hysteresis state
    private var isBlinkActive = false
    private var isSmileActive = false
    private var lastBlinkTime = 0L

    // Wink detection state
    private var leftWinkActive = false
    private var rightWinkActive = false
    private var leftWinkCandidateStart = 0L
    private var rightWinkCandidateStart = 0L
    private var leftWinkReleaseCandidateStart = 0L
    private var rightWinkReleaseCandidateStart = 0L

    companion object {
        // Hysteresis thresholds
        const val SMILE_ENTER_THRESHOLD = 0.35f
        const val SMILE_EXIT_THRESHOLD = 0.25f
        const val BLINK_ENTER_THRESHOLD = 0.55f
        const val BLINK_EXIT_THRESHOLD = 0.45f
        const val BLINK_REFRACTORY_PERIOD = 200L

        // Baseline calibration parameters
        const val BASELINE_SAMPLE_FRAMES = 30
        const val WINK_MIN_DURATION = 150L
        const val WINK_MAX_DURATION = 1000L
        const val WINK_RELEASE_DURATION = 100L
    }

    /**
     * Data class representing the current face state
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
     * Process face state with hysteresis and baseline adaptation
     */
    fun processFaceState(
        leftEyeClose: Float,
        rightEyeClose: Float,
        smoothedSmileScore: Float,
        smoothedBlinkScore: Float,
        headYaw: Float,
        headPitch: Float,
        currentTime: Long
    ): FaceState {

        // Update baselines if needed
        updateBaselines(leftEyeClose, rightEyeClose, smoothedSmileScore)

        // Apply hysteresis for smile detection
        val isCurrentlySmiling = processSmileHysteresis(smoothedSmileScore)

        // Apply hysteresis for blink detection with refractory period
        val isCurrentlyBlinking = processBlinkHysteresis(smoothedBlinkScore, currentTime)

        // Determine eye states (considering blink state)
        val leftEyeOpen = if (isCurrentlyBlinking) false else (leftEyeClose < BLINK_ENTER_THRESHOLD)
        val rightEyeOpen =
            if (isCurrentlyBlinking) false else (rightEyeClose < BLINK_ENTER_THRESHOLD)

        return FaceState(
            leftEyeOpen = leftEyeOpen,
            rightEyeOpen = rightEyeOpen,
            isSmiling = isCurrentlySmiling,
            headRotationY = headYaw,
            headRotationX = headPitch
        )
    }

    private fun updateBaselines(leftEyeClose: Float, rightEyeClose: Float, smileScore: Float) {
        // Simple baseline adaptation - in real implementation you'd have frame counters
        if (!baselineReadyEyes) {
            baselineLeftEyeClose = leftEyeClose
            baselineRightEyeClose = rightEyeClose
            baselineReadyEyes = true
        }

        if (!baselineReadySmile) {
            baselineSmile = smileScore
            baselineReadySmile = true
        }
    }

    private fun processSmileHysteresis(smileScore: Float): Boolean {
        if (!isSmileActive && smileScore > SMILE_ENTER_THRESHOLD) {
            isSmileActive = true
        } else if (isSmileActive && smileScore < SMILE_EXIT_THRESHOLD) {
            isSmileActive = false
        }
        return isSmileActive
    }

    private fun processBlinkHysteresis(blinkScore: Float, currentTime: Long): Boolean {
        val timeSinceLastBlink = currentTime - lastBlinkTime

        if (!isBlinkActive && blinkScore > BLINK_ENTER_THRESHOLD &&
            timeSinceLastBlink > BLINK_REFRACTORY_PERIOD
        ) {
            isBlinkActive = true
            lastBlinkTime = currentTime
        } else if (isBlinkActive && blinkScore < BLINK_EXIT_THRESHOLD) {
            isBlinkActive = false
        }

        return isBlinkActive
    }

    /**
     * Process wink detection with timing constraints
     */
    fun processWinkDetection(
        leftEyeClose: Float,
        rightEyeClose: Float,
        currentTime: Long
    ): Pair<Boolean, Boolean> {
        val leftWink = processLeftWink(leftEyeClose, rightEyeClose, currentTime)
        val rightWink = processRightWink(leftEyeClose, rightEyeClose, currentTime)
        return Pair(leftWink, rightWink)
    }

    private fun processLeftWink(
        leftEyeClose: Float,
        rightEyeClose: Float,
        currentTime: Long
    ): Boolean {
        val leftClosed = leftEyeClose > BLINK_ENTER_THRESHOLD
        val rightOpen = rightEyeClose < BLINK_EXIT_THRESHOLD

        if (!leftWinkActive && leftClosed && rightOpen) {
            leftWinkCandidateStart = currentTime
            leftWinkActive = true
        } else if (leftWinkActive && (!leftClosed || !rightOpen)) {
            leftWinkReleaseCandidateStart = currentTime
            leftWinkActive = false

            val duration = currentTime - leftWinkCandidateStart
            return duration >= WINK_MIN_DURATION && duration <= WINK_MAX_DURATION
        }

        return false
    }

    private fun processRightWink(
        leftEyeClose: Float,
        rightEyeClose: Float,
        currentTime: Long
    ): Boolean {
        val rightClosed = rightEyeClose > BLINK_ENTER_THRESHOLD
        val leftOpen = leftEyeClose < BLINK_EXIT_THRESHOLD

        if (!rightWinkActive && rightClosed && leftOpen) {
            rightWinkCandidateStart = currentTime
            rightWinkActive = true
        } else if (rightWinkActive && (!rightClosed || !leftOpen)) {
            rightWinkReleaseCandidateStart = currentTime
            rightWinkActive = false

            val duration = currentTime - rightWinkCandidateStart
            return duration >= WINK_MIN_DURATION && duration <= WINK_MAX_DURATION
        }

        return false
    }

    fun reset() {
        baselineLeftEyeClose = 0f
        baselineRightEyeClose = 0f
        baselineSmile = 0f
        baselineReadyEyes = false
        baselineReadySmile = false
        isBlinkActive = false
        isSmileActive = false
        lastBlinkTime = 0L
        leftWinkActive = false
        rightWinkActive = false
        leftWinkCandidateStart = 0L
        rightWinkCandidateStart = 0L
        leftWinkReleaseCandidateStart = 0L
        rightWinkReleaseCandidateStart = 0L
    }
}