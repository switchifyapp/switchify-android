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
 *
 * Some devices need coordinate transformations in portrait mode, others work correctly
 * with raw values. This class provides device-specific detection with caching for
 * optimal performance.
 *
 * Default behavior: No transformations needed (works for most devices)
 * Custom systems: Can be set manually for specific device configurations
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

            // Using cached coordinate system

            return CoordinateSystem(
                pitchInverted = pitchInverted,
                yawInverted = yawInverted,
                confidence = confidence,
                deviceKey = deviceKey
            )
        }

        // No valid cached data, return default
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

        // Coordinate system saved to preferences
    }

    /**
     * Get default coordinate system based on conservative defaults
     * Most devices work with no transformations in portrait mode
     */
    private fun getDefaultCoordinateSystem(deviceKey: String): CoordinateSystem {
        // Using default coordinate system (no transformations)

        return CoordinateSystem(
            pitchInverted = false,  // Most devices don't need pitch inversion
            yawInverted = false,    // Most devices don't need yaw inversion
            confidence = 0.8f,     // High confidence in conservative default
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
        // Coordinate system cache cleared
    }

    /**
     * Create a coordinate system with specific transformations for testing
     * @param pitchInverted Whether pitch should be inverted
     * @param yawInverted Whether yaw should be inverted
     */
    fun createCustomCoordinateSystem(pitchInverted: Boolean, yawInverted: Boolean): CoordinateSystem {
        val deviceKey = getDeviceKey()
        val customSystem = CoordinateSystem(
            pitchInverted = pitchInverted,
            yawInverted = yawInverted,
            confidence = 1.0f, // Manual configuration = high confidence
            deviceKey = deviceKey
        )

        saveCoordinateSystem(customSystem)
        // Custom coordinate system saved

        return customSystem
    }
}