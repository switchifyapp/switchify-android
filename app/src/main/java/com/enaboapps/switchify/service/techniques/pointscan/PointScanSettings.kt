package com.enaboapps.switchify.service.techniques.pointscan

import android.content.Context
import android.content.Intent
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.ContinuousLineSpeedUtils
import com.enaboapps.switchify.utils.Resources

object PointScanSettings {

    private var preferenceManager: PreferenceManager? = null

    const val CURSOR_SETTINGS_CHANGED_ACTION = "com.enaboapps.switchify.CURSOR_SETTINGS_CHANGED"

    object Modes {
        const val MODE_SINGLE = "single"
        const val MODE_BLOCK = "block"
    }

    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
    }

    private fun broadcastChanged(context: Context) {
        context.sendBroadcast(
            Intent(CURSOR_SETTINGS_CHANGED_ACTION).setPackage(context.packageName)
        )
    }

    fun getMode(): String {
        preferenceManager?.let { preferenceManager ->
            val storedMode = preferenceManager.getStringValue(
                PreferenceManager.PREFERENCE_KEY_CURSOR_MODE
            )
            return if (storedMode == Modes.MODE_SINGLE || storedMode == Modes.MODE_BLOCK) {
                storedMode
            } else {
                Modes.MODE_SINGLE
            }
        }
        return Modes.MODE_SINGLE
    }

    fun setMode(mode: String, context: Context) {
        preferenceManager?.setStringValue(
            PreferenceManager.PREFERENCE_KEY_CURSOR_MODE,
            mode
        )
        broadcastChanged(context)
    }

    fun isSingleMode(): Boolean {
        return getMode() == Modes.MODE_SINGLE
    }

    fun isBlockMode(): Boolean {
        return getMode() == Modes.MODE_BLOCK
    }

    fun getModeName(mode: String): String {
        return when (mode) {
            Modes.MODE_SINGLE -> Resources.getString(R.string.point_scan_mode_line_only)
            Modes.MODE_BLOCK -> Resources.getString(R.string.point_scan_mode_grid_line)
            else -> Resources.getString(R.string.unknown)
        }
    }

    fun getModeDescription(mode: String): String {
        return when (mode) {
            Modes.MODE_SINGLE -> Resources.getString(R.string.point_scan_mode_desc_line_only)
            Modes.MODE_BLOCK -> Resources.getString(R.string.point_scan_mode_desc_grid_line)
            else -> Resources.getString(R.string.unknown)
        }
    }

    /**
     * Get the point scan block scan rate
     * @return The block scan rate
     */
    fun getCursorBlockScanRate(): Long {
        return preferenceManager?.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE)
            ?: 1000L
    }

    /**
     * Get the point scan block count
     * @return The block count
     */
    fun getCursorBlockCount(): Int {
        return preferenceManager?.getStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
            "4"
        )?.toInt() ?: 4
    }

    /**
     * Get the line speed as time interval (converted from speed level)
     * @return The time interval in milliseconds
     */
    fun getFineCursorScanRate(): Long {
        val speedLevel = preferenceManager?.getIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_POINT_SCAN_LINE_SPEED_LEVEL,
            ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        )
            ?: ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        return ContinuousLineSpeedUtils.speedLevelToInterval(speedLevel)
    }

    /**
     * Get the line speed level (1-25 scale)
     * @return The speed level where 1 = slowest, 25 = fastest
     */
    fun getLineSpeedLevel(): Int {
        return preferenceManager?.getIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_POINT_SCAN_LINE_SPEED_LEVEL,
            ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        )
            ?: ContinuousLineSpeedUtils.getDefaultSpeedLevel()
    }

    /**
     * Set the line speed level (1-25 scale)
     * @param speedLevel The speed level where 1 = slowest, 25 = fastest
     */
    fun setLineSpeedLevel(speedLevel: Int, context: Context) {
        val clampedSpeed = speedLevel.coerceIn(1, 25)
        preferenceManager?.setIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_POINT_SCAN_LINE_SPEED_LEVEL,
            clampedSpeed
        )
        broadcastChanged(context)
    }

    /**
     * Convert line speed level to time interval in milliseconds
     * @param speedLevel The speed level (1-25)
     * @return Time interval in milliseconds
     */
    fun speedLevelToInterval(speedLevel: Int): Long {
        return ContinuousLineSpeedUtils.speedLevelToInterval(speedLevel)
    }

    /**
     * Get speed level description for UI display
     * @param speedLevel The speed level (1-25)
     * @return User-friendly description
     */
    fun getSpeedLevelDescription(speedLevel: Int): String {
        return ContinuousLineSpeedUtils.getSpeedLevelDescription(speedLevel)
    }

    /**
     * Set the point scan block count
     * @param count The block count
     */
    fun setCursorBlockCount(count: Int, context: Context) {
        preferenceManager?.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
            count.toString()
        )
        broadcastChanged(context)
    }

    /**
     * Set the point scan block scan rate
     * @param rate The block scan rate
     */
    fun setCursorBlockScanRate(rate: Long, context: Context) {
        preferenceManager?.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE,
            rate
        )
        broadcastChanged(context)
    }

    /**
     * Set the fine point scan rate using speed level
     * @param speedLevel The speed level (1-25)
     */
    fun setFineCursorScanRate(speedLevel: Int, context: Context) {
        setLineSpeedLevel(speedLevel, context)
    }
}
