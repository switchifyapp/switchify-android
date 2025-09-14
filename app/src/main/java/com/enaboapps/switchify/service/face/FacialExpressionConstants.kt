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
        /** Threshold for entering smile detection state (0.0-1.0 confidence) */
        const val SMILE_ENTER_THRESHOLD = 0.35f
        /** Threshold for exiting smile detection state (0.0-1.0 confidence) */
        const val SMILE_EXIT_THRESHOLD = 0.25f
        
        /** Threshold for entering blink/wink detection state (0.0-1.0 confidence) */
        const val BLINK_ENTER_THRESHOLD = 0.55f
        /** Threshold for exiting blink/wink detection state (0.0-1.0 confidence) */
        const val BLINK_EXIT_THRESHOLD = 0.45f
    }

    /**
     * MediaPipe facial landmark indices for head pose estimation
     * Based on the 468-point face landmark model
     */
    object LandmarkIndices {
        /** MediaPipe landmark index for nose tip position */
        const val NOSE_TIP_INDEX = 1
        /** MediaPipe landmark index for chin position */
        const val CHIN_INDEX = 152
        /** MediaPipe landmark index for left eye outer corner */
        const val LEFT_EYE_OUTER_INDEX = 33
        /** MediaPipe landmark index for right eye outer corner */
        const val RIGHT_EYE_OUTER_INDEX = 263
    }

    /**
     * Signal processing constants for facial expression detection
     */
    object SignalProcessing {
        /** Exponential Moving Average smoothing factor (0.0 = no smoothing, 1.0 = max smoothing) */
        const val EMA_ALPHA = 0.3f
        
        /** Refractory period for blink detection to prevent multiple triggers (milliseconds) */
        const val BLINK_REFRACTORY_PERIOD = 200L
    }

    /**
     * Default hold times for facial expressions (milliseconds)
     * These are the fallback values when preferences are not set
     */
    object DefaultHoldTimes {
        /** Default hold time for smile gesture (milliseconds) */
        const val SMILE_HOLD_TIME = 500L
        /** Default hold time for left wink gesture (milliseconds) */
        const val LEFT_WINK_HOLD_TIME = 300L
        /** Default hold time for right wink gesture (milliseconds) */
        const val RIGHT_WINK_HOLD_TIME = 300L
        /** Default hold time for blink gesture (milliseconds) */
        const val BLINK_HOLD_TIME = 400L
        /** Default hold time for head turn gestures - instant trigger (milliseconds) */
        const val HEAD_TURN_HOLD_TIME = 0L
        /** Default fallback hold time for unknown gestures (milliseconds) */
        const val DEFAULT_FALLBACK_TIME = 500L
    }
}