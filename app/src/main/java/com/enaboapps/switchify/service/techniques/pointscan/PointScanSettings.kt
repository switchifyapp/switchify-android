package com.enaboapps.switchify.service.techniques.pointscan

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.ContinuousLineSpeedUtils
import com.enaboapps.switchify.utils.Resources

object PointScanSettings {

    private var preferenceManager: PreferenceManager? = null

    object Modes {
        const val MODE_SINGLE = "single"
        const val MODE_BLOCK = "block"
    }

    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
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

    fun setMode(mode: String) {
        preferenceManager?.setStringValue(
            PreferenceManager.PREFERENCE_KEY_CURSOR_MODE,
            mode
        )
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

    fun getLineSpeedLevel(): Int {
        val storedLevel = preferenceManager?.getIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_POINT_SCAN_LINE_SPEED_LEVEL,
            ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        )
            ?: ContinuousLineSpeedUtils.getDefaultSpeedLevel()
        return ContinuousLineSpeedUtils.getRepresentativeLevel(storedLevel)
    }

    fun setLineSpeedLevel(speedLevel: Int) {
        val representativeLevel = ContinuousLineSpeedUtils.getRepresentativeLevel(speedLevel)
        preferenceManager?.setIntegerValue(
            PreferenceManager.Keys.PREFERENCE_KEY_POINT_SCAN_LINE_SPEED_LEVEL,
            representativeLevel
        )
    }

    fun getLineSpeedPxPerSecond(context: Context): Float {
        return ContinuousLineSpeedUtils.getLinearSpeedPxPerSecond(context, getLineSpeedLevel())
    }

    fun getSpeedLevelDescription(speedLevel: Int): String {
        return ContinuousLineSpeedUtils.getDisplayName(speedLevel)
    }

    /**
     * Set the point scan block count
     * @param count The block count
     */
    fun setCursorBlockCount(count: Int) {
        preferenceManager?.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
            count.toString()
        )
    }

    /**
     * Set the point scan block scan rate
     * @param rate The block scan rate
     */
    fun setCursorBlockScanRate(rate: Long) {
        preferenceManager?.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE,
            rate
        )
    }

    /**
     * Set the fine point scan rate using speed level
     * @param speedLevel The speed level (1-25)
     */
    fun setFineCursorScanRate(speedLevel: Int) {
        setLineSpeedLevel(speedLevel)
    }
}
