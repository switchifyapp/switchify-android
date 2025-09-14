package com.enaboapps.switchify.service.face

/**
 * Unified constants for facial expression detection using MediaPipe
 * Eliminates duplication across FaceProcessingService and other components
 */
object FacialExpressionConstants {

    /**
     * MediaPipe blend shape category names for facial expressions
     */
    object BlendShapeNames {
        const val EYE_BLINK_LEFT = "eyeBlinkLeft"
        const val EYE_BLINK_RIGHT = "eyeBlinkRight"
        const val EYE_SQUINT_LEFT = "eyeSquintLeft"
        const val EYE_SQUINT_RIGHT = "eyeSquintRight"
        const val MOUTH_SMILE_LEFT = "mouthSmileLeft"
        const val MOUTH_SMILE_RIGHT = "mouthSmileRight"
    }

    /**
     * Hysteresis thresholds for facial expression detection
     * Using separate enter/exit thresholds prevents flickering
     */
    object DetectionThresholds {
        // Smile detection thresholds
        const val SMILE_ENTER_THRESHOLD = 0.35f
        const val SMILE_EXIT_THRESHOLD = 0.25f
        
        // Blink/wink detection thresholds
        const val BLINK_ENTER_THRESHOLD = 0.55f
        const val BLINK_EXIT_THRESHOLD = 0.45f
    }

    /**
     * MediaPipe facial landmark indices for head pose estimation
     * Based on the 468-point face landmark model
     */
    object LandmarkIndices {
        const val NOSE_TIP_INDEX = 1
        const val CHIN_INDEX = 152
        const val LEFT_EYE_OUTER_INDEX = 33
        const val RIGHT_EYE_OUTER_INDEX = 263
    }

    /**
     * Signal processing constants for facial expression detection
     */
    object SignalProcessing {
        // Exponential Moving Average smoothing factor (0.0 = no smoothing, 1.0 = max smoothing)
        const val EMA_ALPHA = 0.3f
        
        // Refractory period for blink detection to prevent multiple triggers (ms)
        const val BLINK_REFRACTORY_PERIOD = 200L
    }

    /**
     * Default hold times for facial expressions (milliseconds)
     * These are the fallback values when preferences are not set
     */
    object DefaultHoldTimes {
        const val SMILE_HOLD_TIME = 500L
        const val LEFT_WINK_HOLD_TIME = 300L
        const val RIGHT_WINK_HOLD_TIME = 300L
        const val BLINK_HOLD_TIME = 400L
        const val HEAD_TURN_HOLD_TIME = 0L // Instant trigger
        const val DEFAULT_FALLBACK_TIME = 500L
    }
}