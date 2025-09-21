package com.enaboapps.switchify.service.techniques.radar

import android.content.Context
import android.content.Intent
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.ContinuousLineSpeedUtils

/**
 * Settings class for radar scanning technique
 * Handles all radar-specific configuration and preferences
 */
object RadarSettings {

    private var preferenceManager: PreferenceManager? = null

    const val RADAR_SETTINGS_CHANGED_ACTION = "com.enaboapps.switchify.RADAR_SETTINGS_CHANGED"

    object StartingPosition {
        const val TOP = "top"
        const val BOTTOM = "bottom"
    }

    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
    }

    private fun broadcastChanged(context: Context) {
        context.sendBroadcast(
            Intent(RADAR_SETTINGS_CHANGED_ACTION).setPackage(context.packageName)
        )
    }

    /**
     * Get the radar speed as time interval (converted from speed level)
     * @return The radar scan rate
     */
    fun getScanRate(): Long {
        val speedLevel = preferenceManager?.getIntegerValue(PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SPEED_LEVEL, ContinuousLineSpeedUtils.getDefaultSpeedLevel()) ?: ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        return ContinuousLineSpeedUtils.speedLevelToInterval(speedLevel)
    }

    /**
     * Get the radar speed level (1-25 scale)
     * @return The speed level where 1 = slowest, 25 = fastest
     */
    fun getSpeedLevel(): Int {
        return preferenceManager?.getIntegerValue(PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SPEED_LEVEL, ContinuousLineSpeedUtils.getDefaultSpeedLevel()) ?: ContinuousLineSpeedUtils.getDefaultSpeedLevel()
    }

    /**
     * Set the radar speed level (1-25 scale)
     * @param speedLevel The speed level where 1 = slowest, 25 = fastest
     * @param context Context for broadcasting changes
     */
    fun setSpeedLevel(speedLevel: Int, context: Context) {
        val clampedSpeed = speedLevel.coerceIn(1, 25)
        preferenceManager?.setIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SPEED_LEVEL,
            clampedSpeed
        )
        broadcastChanged(context)
    }

    /**
     * Convert radar speed level to time interval in milliseconds
     * @param speedLevel The speed level (1-25)
     * @return Time interval in milliseconds
     */
    fun speedLevelToInterval(speedLevel: Int): Long {
        return ContinuousLineSpeedUtils.speedLevelToInterval(speedLevel)
    }

    /**
     * Get radar speed level description for UI display
     * @param speedLevel The speed level (1-25)
     * @return User-friendly description
     */
    fun getSpeedLevelDescription(speedLevel: Int): String {
        return ContinuousLineSpeedUtils.getSpeedLevelDescription(speedLevel)
    }

    /**
     * Check if radar slow down then select mode is enabled
     * @return true if radar slow down then select mode is enabled, false otherwise
     */
    fun isSlowDownThenSelectEnabled(): Boolean {
        return preferenceManager?.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SLOW_DOWN_THEN_SELECT) ?: false
    }

    /**
     * Set radar slow down then select mode
     * @param enabled Whether to enable slow down then select
     * @param context Context for broadcasting changes
     */
    fun setSlowDownThenSelectEnabled(enabled: Boolean, context: Context) {
        preferenceManager?.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SLOW_DOWN_THEN_SELECT,
            enabled
        )
        broadcastChanged(context)
    }

    /**
     * Get the radar starting position
     * @return The radar starting position (top or bottom)
     */
    fun getStartingPosition(): String {
        return preferenceManager?.getStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_STARTING_POSITION,
            StartingPosition.TOP
        ) ?: StartingPosition.TOP
    }

    /**
     * Set the radar starting position
     * @param position The starting position (top or bottom)
     * @param context Context for broadcasting changes
     */
    fun setStartingPosition(position: String, context: Context) {
        preferenceManager?.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_STARTING_POSITION,
            position
        )
        broadcastChanged(context)
    }

    /**
     * Check if starting position is top
     * @return true if starting from top, false otherwise
     */
    fun isStartingFromTop(): Boolean {
        return getStartingPosition() == StartingPosition.TOP
    }

    /**
     * Check if starting position is bottom
     * @return true if starting from bottom, false otherwise
     */
    fun isStartingFromBottom(): Boolean {
        return getStartingPosition() == StartingPosition.BOTTOM
    }
}