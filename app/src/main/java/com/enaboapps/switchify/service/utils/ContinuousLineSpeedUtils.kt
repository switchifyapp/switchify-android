package com.enaboapps.switchify.service.utils

/**
 * Utility class for continuous line movement speed calculations
 * Used by techniques that move lines continuously (point scan, radar)
 * as opposed to discrete item-by-item scanning
 */
object ContinuousLineSpeedUtils {

    /**
     * Convert speed level to time interval in milliseconds for continuous line movement
     * @param speedLevel The speed level (1-25) where 1 = slowest, 25 = fastest
     * @return Time interval in milliseconds between line movements
     */
    fun speedLevelToInterval(speedLevel: Int): Long {
        val clampedSpeed = speedLevel.coerceIn(1, 25)
        // Formula: timeInterval = 10 + (25 - speedLevel) * 48
        // Speed 1 = 1162ms, Speed 25 = 10ms
        return 10L + (25 - clampedSpeed) * 48L
    }

    /**
     * Get speed level description for UI display
     * @param speedLevel The speed level (1-25)
     * @return User-friendly description of the speed level
     */
    fun getSpeedLevelDescription(speedLevel: Int): String {
        return when (speedLevel.coerceIn(1, 25)) {
            in 1..6 -> "Very Slow"
            in 7..12 -> "Slow"
            in 13..18 -> "Medium"
            in 19..25 -> "Fast"
            else -> "Medium"
        }
    }

    /**
     * Validate that a speed level is within the acceptable range
     * @param speedLevel The speed level to validate
     * @return True if the speed level is valid (1-25), false otherwise
     */
    fun isValidSpeedLevel(speedLevel: Int): Boolean {
        return speedLevel in 1..25
    }

    /**
     * Get the default speed level for continuous line movement
     * @return The default speed level (13 = Medium)
     */
    fun getDefaultSpeedLevel(): Int {
        return 13
    }
}