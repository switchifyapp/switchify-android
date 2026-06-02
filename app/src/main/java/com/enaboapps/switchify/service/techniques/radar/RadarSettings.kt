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

    fun getSpeedLevel(): Int {
        val storedLevel = preferenceManager?.getIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SPEED_LEVEL,
            ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        ) ?: ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        return ContinuousLineSpeedUtils.getRepresentativeLevel(storedLevel)
    }

    fun setSpeedLevel(speedLevel: Int, context: Context) {
        val representativeLevel = ContinuousLineSpeedUtils.getRepresentativeLevel(speedLevel)
        preferenceManager?.setIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SPEED_LEVEL,
            representativeLevel
        )
        broadcastChanged(context)
    }

    fun getLinearSpeedPxPerSecond(context: Context, slowDownFactor: Float = 1f): Float {
        return ContinuousLineSpeedUtils.getLinearSpeedPxPerSecond(context, getSpeedLevel()) / slowDownFactor
    }

    fun getAngularSpeedDegreesPerSecond(slowDownFactor: Float = 1f): Float {
        return ContinuousLineSpeedUtils.getRadarAngularSpeedDegreesPerSecond(getSpeedLevel()) / slowDownFactor
    }

    fun getSpeedLevelDescription(speedLevel: Int): String {
        return ContinuousLineSpeedUtils.getDisplayName(speedLevel)
    }

    /**
     * Check if radar slow down then select mode is enabled
     * @return true if radar slow down then select mode is enabled, false otherwise
     */
    fun isSlowDownThenSelectEnabled(): Boolean {
        return preferenceManager?.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SLOW_DOWN_THEN_SELECT)
            ?: false
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
