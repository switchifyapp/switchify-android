package com.enaboapps.switchify.service.techniques.radar

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.ContinuousLineSpeedUtils
import com.enaboapps.switchify.utils.Resources

/**
 * Settings class for radar scanning technique
 * Handles all radar-specific configuration and preferences
 */
object RadarSettings {

    private var preferenceManager: PreferenceManager? = null

    object StartingPosition {
        const val TOP = "top"
        const val BOTTOM = "bottom"
    }

    object Modes {
        const val MODE_SINGLE = "single"
        const val MODE_BLOCK = "block"
    }

    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
    }

    fun getMode(): String {
        val storedMode = preferenceManager?.getStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_MODE,
            Modes.MODE_SINGLE
        )
        return if (storedMode == Modes.MODE_BLOCK) Modes.MODE_BLOCK else Modes.MODE_SINGLE
    }

    fun setMode(mode: String) {
        preferenceManager?.setStringValue(PreferenceManager.Keys.PREFERENCE_KEY_RADAR_MODE, mode)
    }

    fun isSingleMode(): Boolean = getMode() == Modes.MODE_SINGLE

    fun isBlockMode(): Boolean = getMode() == Modes.MODE_BLOCK

    fun getModeName(mode: String): String {
        return when (mode) {
            Modes.MODE_SINGLE -> Resources.getString(R.string.radar_mode_radar_only)
            Modes.MODE_BLOCK -> Resources.getString(R.string.radar_mode_grid_radar)
            else -> Resources.getString(R.string.unknown)
        }
    }

    fun getModeDescription(mode: String): String {
        return when (mode) {
            Modes.MODE_SINGLE -> Resources.getString(R.string.radar_mode_desc_radar_only)
            Modes.MODE_BLOCK -> Resources.getString(R.string.radar_mode_desc_grid_radar)
            else -> Resources.getString(R.string.unknown)
        }
    }

    fun getBlockScanRate(): Long {
        return preferenceManager?.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_RADAR_BLOCK_SCAN_RATE, 1000L)
            ?: 1000L
    }

    fun setBlockScanRate(rate: Long) {
        preferenceManager?.setLongValue(PreferenceManager.Keys.PREFERENCE_KEY_RADAR_BLOCK_SCAN_RATE, rate)
    }

    fun getBlockCount(): Int {
        return preferenceManager?.getStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_BLOCK_COUNT,
            "4"
        )?.toIntOrNull() ?: 4
    }

    fun setBlockCount(count: Int) {
        preferenceManager?.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_BLOCK_COUNT,
            count.toString()
        )
    }

    fun getSpeedLevel(): Int {
        val storedLevel = preferenceManager?.getIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SPEED_LEVEL,
            ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        ) ?: ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        return ContinuousLineSpeedUtils.getRepresentativeLevel(storedLevel)
    }

    fun setSpeedLevel(speedLevel: Int) {
        val representativeLevel = ContinuousLineSpeedUtils.getRepresentativeLevel(speedLevel)
        preferenceManager?.setIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SPEED_LEVEL,
            representativeLevel
        )
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
     */
    fun setSlowDownThenSelectEnabled(enabled: Boolean) {
        preferenceManager?.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SLOW_DOWN_THEN_SELECT,
            enabled
        )
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
     */
    fun setStartingPosition(position: String) {
        preferenceManager?.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_RADAR_STARTING_POSITION,
            position
        )
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
