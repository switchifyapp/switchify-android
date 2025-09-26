package com.enaboapps.switchify.service.face.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Detects the correct coordinate system for head pose on different devices.
 * Some devices need coordinate transformations, others work correctly with raw values.
 */
class CoordinateSystemDetector(private val context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        "coordinate_system_detection", Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "CoordinateSystemDetector"
        private const val PREF_DEVICE_KEY = "device_key"
        private const val PREF_PITCH_INVERTED = "pitch_inverted"
        private const val PREF_YAW_INVERTED = "yaw_inverted"
        private const val PREF_DETECTION_CONFIDENCE = "detection_confidence"
        private const val PREF_LAST_DETECTION_VERSION = "last_detection_version"

        // Version for detection algorithm - increment when logic changes
        private const val DETECTION_VERSION = 1
    }

    /**
     * Represents the coordinate system characteristics for a device
     */
    data class CoordinateSystem(
        val pitchInverted: Boolean = false,
        val yawInverted: Boolean = false,
        val confidence: Float = 0.0f,
        val deviceKey: String = ""
    ) {
        fun shouldApplyPitchInversion(): Boolean = pitchInverted
        fun shouldApplyYawInversion(): Boolean = yawInverted
        fun isHighConfidence(): Boolean = confidence > 0.7f
    }

    /**
     * Get the coordinate system for this device, using cached result if available
     */
    fun getCoordinateSystem(): CoordinateSystem {
        val deviceKey = getDeviceKey()
        val storedDeviceKey = preferences.getString(PREF_DEVICE_KEY, "")
        val detectionVersion = preferences.getInt(PREF_LAST_DETECTION_VERSION, 0)

        // Check if we have valid cached data for this device and detection version
        if (storedDeviceKey == deviceKey && detectionVersion == DETECTION_VERSION) {
            val pitchInverted = preferences.getBoolean(PREF_PITCH_INVERTED, false)
            val yawInverted = preferences.getBoolean(PREF_YAW_INVERTED, false)
            val confidence = preferences.getFloat(PREF_DETECTION_CONFIDENCE, 0.0f)

            Log.d(TAG, "Using cached coordinate system for $deviceKey: pitch=$pitchInverted, yaw=$yawInverted, confidence=$confidence")

            return CoordinateSystem(
                pitchInverted = pitchInverted,
                yawInverted = yawInverted,
                confidence = confidence,
                deviceKey = deviceKey
            )
        }

        // No valid cached data, return default and trigger detection
        Log.d(TAG, "No cached coordinate system for $deviceKey, using defaults")
        return getDefaultCoordinateSystem(deviceKey)
    }

    /**
     * Save detected coordinate system to preferences
     */
    fun saveCoordinateSystem(coordinateSystem: CoordinateSystem) {
        preferences.edit().apply {
            putString(PREF_DEVICE_KEY, coordinateSystem.deviceKey)
            putBoolean(PREF_PITCH_INVERTED, coordinateSystem.pitchInverted)
            putBoolean(PREF_YAW_INVERTED, coordinateSystem.yawInverted)
            putFloat(PREF_DETECTION_CONFIDENCE, coordinateSystem.confidence)
            putInt(PREF_LAST_DETECTION_VERSION, DETECTION_VERSION)
            apply()
        }

        Log.i(TAG, "Saved coordinate system for ${coordinateSystem.deviceKey}: " +
              "pitch=${coordinateSystem.pitchInverted}, yaw=${coordinateSystem.yawInverted}, " +
              "confidence=${coordinateSystem.confidence}")
    }

    /**
     * Get default coordinate system based on known device patterns
     */
    private fun getDefaultCoordinateSystem(deviceKey: String): CoordinateSystem {
        // Known device patterns - these are educated guesses that can be overridden by detection
        val pitchInverted = when {
            deviceKey.contains("samsung") -> false
            deviceKey.contains("pixel") -> false
            deviceKey.contains("xiaomi") -> true
            deviceKey.contains("oneplus") -> false
            else -> false // Conservative default
        }

        val yawInverted = when {
            deviceKey.contains("samsung") -> true
            deviceKey.contains("pixel") -> true
            deviceKey.contains("xiaomi") -> false
            deviceKey.contains("oneplus") -> true
            else -> true // Conservative default - most devices need yaw inversion
        }

        Log.d(TAG, "Using default coordinate system for $deviceKey: pitch=$pitchInverted, yaw=$yawInverted")

        return CoordinateSystem(
            pitchInverted = pitchInverted,
            yawInverted = yawInverted,
            confidence = 0.5f, // Medium confidence for defaults
            deviceKey = deviceKey
        )
    }

    /**
     * Generate a unique key for this device
     */
    private fun getDeviceKey(): String {
        return "${Build.BRAND.lowercase()}_${Build.MODEL.lowercase().replace(" ", "_").replace("-", "_")}"
    }

    /**
     * Clear cached coordinate system (for testing or after major device changes)
     */
    fun clearCache() {
        preferences.edit().clear().apply()
        Log.d(TAG, "Cleared coordinate system cache")
    }

    /**
     * Runtime detection class for analyzing head movements during actual usage
     */
    class RuntimeDetector {
        private val samples = mutableListOf<DetectionSample>()
        private var isDetecting = false

        data class DetectionSample(
            val timestamp: Long,
            val rawPitch: Float,
            val rawYaw: Float,
            val noseTipY: Float,
            val foreheadY: Float,
            val leftEarY: Float,
            val rightEarY: Float
        )

        /**
         * Start runtime detection process
         */
        fun startDetection() {
            isDetecting = true
            samples.clear()
            Log.d(TAG, "Started runtime coordinate system detection")
        }

        /**
         * Add sample during face processing
         */
        fun addSample(
            rawPitch: Float,
            rawYaw: Float,
            faceLandmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>?
        ) {
            if (!isDetecting || faceLandmarks == null || faceLandmarks.size < 468) return

            // Key landmark indices for face orientation
            val noseTip = faceLandmarks[1]      // Nose tip
            val forehead = faceLandmarks[9]     // Forehead center
            val leftEar = faceLandmarks[234]    // Left ear
            val rightEar = faceLandmarks[454]   // Right ear

            samples.add(DetectionSample(
                timestamp = System.currentTimeMillis(),
                rawPitch = rawPitch,
                rawYaw = rawYaw,
                noseTipY = noseTip.y(),
                foreheadY = forehead.y(),
                leftEarY = leftEar.y(),
                rightEarY = rightEar.y()
            ))

            // Stop detection after collecting enough samples
            if (samples.size >= 200) {
                stopDetectionAndAnalyze()
            }
        }

        /**
         * Stop detection and analyze collected samples
         */
        private fun stopDetectionAndAnalyze(): CoordinateSystem? {
            isDetecting = false

            if (samples.size < 50) {
                Log.w(TAG, "Not enough samples for detection: ${samples.size}")
                return null
            }

            return analyzeMovementPatterns()
        }

        /**
         * Analyze movement patterns to determine coordinate system
         */
        private fun analyzeMovementPatterns(): CoordinateSystem {
            // Analyze pitch direction by comparing geometric head position with reported pitch
            val upMovements = mutableListOf<Float>()
            val downMovements = mutableListOf<Float>()
            val leftMovements = mutableListOf<Float>()
            val rightMovements = mutableListOf<Float>()

            for (sample in samples) {
                // Determine geometric head orientation from landmarks
                val isLookingUp = sample.noseTipY < sample.foreheadY * 0.95f
                val isLookingDown = sample.noseTipY > sample.foreheadY * 1.05f
                val isLookingLeft = sample.leftEarY < sample.rightEarY * 0.95f
                val isLookingRight = sample.rightEarY < sample.leftEarY * 0.95f

                // Collect pitch samples based on geometric orientation
                when {
                    isLookingUp -> upMovements.add(sample.rawPitch)
                    isLookingDown -> downMovements.add(sample.rawPitch)
                }

                // Collect yaw samples based on geometric orientation
                when {
                    isLookingLeft -> leftMovements.add(sample.rawYaw)
                    isLookingRight -> rightMovements.add(sample.rawYaw)
                }
            }

            // Determine if pitch is inverted
            val avgUpPitch = upMovements.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val avgDownPitch = downMovements.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val pitchInverted = avgUpPitch < avgDownPitch // Looking up should give positive pitch

            // Determine if yaw is inverted
            val avgLeftYaw = leftMovements.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val avgRightYaw = rightMovements.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            val yawInverted = avgLeftYaw > avgRightYaw // Looking left should give negative yaw

            // Calculate confidence based on sample size and consistency
            val confidence = calculateConfidence(upMovements.size, downMovements.size,
                                               leftMovements.size, rightMovements.size)

            Log.i(TAG, "Runtime detection complete: pitch=$pitchInverted, yaw=$yawInverted, " +
                  "confidence=$confidence (samples: up=${upMovements.size}, down=${downMovements.size}, " +
                  "left=${leftMovements.size}, right=${rightMovements.size})")

            return CoordinateSystem(
                pitchInverted = pitchInverted,
                yawInverted = yawInverted,
                confidence = confidence,
                deviceKey = "" // Will be set by caller
            )
        }

        /**
         * Calculate confidence based on sample distribution and consistency
         */
        private fun calculateConfidence(upSamples: Int, downSamples: Int,
                                      leftSamples: Int, rightSamples: Int): Float {
            val totalSamples = upSamples + downSamples + leftSamples + rightSamples
            val minSamplesPerDirection = 10

            // Low confidence if we don't have enough samples in each direction
            if (upSamples < minSamplesPerDirection || downSamples < minSamplesPerDirection ||
                leftSamples < minSamplesPerDirection || rightSamples < minSamplesPerDirection) {
                return 0.3f
            }

            // Medium confidence for balanced samples
            if (totalSamples > 100) {
                return 0.8f
            }

            // Lower confidence for fewer samples
            return 0.6f
        }

        fun isDetectionActive(): Boolean = isDetecting
    }
}