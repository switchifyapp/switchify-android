package com.enaboapps.switchify.service.scanning

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GestureManager

/**
 * A convenience class to get the scan settings
 * @param context The context to use
 */
class ScanSettings(context: Context) {
    private val preferenceManager = PreferenceManager(context)

    /**
     * Check if the scan mode is auto
     * @return true if the scan mode is auto, false otherwise
     */
    fun isAutoScanMode(): Boolean {
        return ScanMode.fromId(preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE)).id == ScanMode.Modes.MODE_AUTO
    }

    /**
     * Check if the scan mode is manual
     * @return true if the scan mode is manual, false otherwise
     */
    fun isManualScanMode(): Boolean {
        return ScanMode.fromId(preferenceManager.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE)).id == ScanMode.Modes.MODE_MANUAL
    }

    /**
     * Get the scan rate
     * @return The scan rate
     */
    fun getScanRate(): Long {
        return preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_RATE)
    }

    /**
     * Check if item scan speech is enabled
     * @return true if item scan speech is enabled, false otherwise
     */
    fun isItemScanSpeechEnabled(): Boolean {
        return preferenceManager.getBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_ITEM_SCAN_SPEECH,
            false
        )
    }

    /**
     * Get the scan cycles
     * @return The scan cycles
     */
    fun getScanCycles(): Int {
        return preferenceManager.getStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_SCAN_CYCLES,
            "3"
        ).toInt()
    }

    /**
     * Get the radar scan rate
     * @return The radar scan rate
     */
    fun getRadarScanRate(): Long {
        return preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SCAN_RATE)
    }

    /**
     * Get the automatically start scan after selection
     * @return The automatically start scan after selection
     */
    fun getAutomaticallyStartScanAfterSelection(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTOMATICALLY_START_SCAN_AFTER_SELECTION) &&
                isAutoScanMode() &&
                !GestureManager.instance.isGestureLockEnabled()
    }

    /**
     * Check if the pause on first item is enabled
     * @return true if the pause on first item is enabled, false otherwise
     */
    fun isPauseOnFirstItemEnabled(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM)
    }

    /**
     * Get the pause on first item delay
     * @return The pause on first item delay if enabled, 0 otherwise
     */
    fun getPauseOnFirstItemDelay(): Long {
        return if (isPauseOnFirstItemEnabled()) {
            preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM_DELAY)
        } else {
            0
        }
    }

    /**
     * Check if the auto select is enabled
     * @return true if the auto select is enabled, false otherwise
     */
    fun isAutoSelectEnabled(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SELECT)
    }

    /**
     * Get the auto select delay
     * @return The auto select delay if enabled, 0 otherwise
     */
    fun getAutoSelectDelay(): Long {
        return if (isAutoSelectEnabled()) {
            preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SELECT_DELAY)
        } else {
            0
        }
    }

    /**
     * Check if directly select keyboard keys is enabled
     * @return true if directly select keyboard keys is enabled, false otherwise
     */
    fun isDirectlySelectKeyboardKeysEnabled(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_DIRECTLY_SELECT_KEYBOARD_KEYS)
    }

    /**
     * Check if row column scan is enabled
     * @return true if row column scan is enabled, false otherwise
     */
    fun isRowColumnScanEnabled(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_ROW_COLUMN_SCAN)
    }

    /**
     * Check if group scan is enabled
     * @return true if group scan is enabled, false otherwise
     */
    fun isGroupScanEnabled(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_GROUP_SCAN)
    }

    /**
     * Check if the move repeat is enabled
     * @return true if the move repeat is enabled, false otherwise
     */
    fun isMoveRepeatEnabled(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_MOVE_REPEAT) == true &&
                isManualScanMode()
    }

    /**
     * Get the move repeat delay
     * @return The move repeat delay if enabled, 0 otherwise
     */
    fun getMoveRepeatDelay(): Long {
        return preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_MOVE_REPEAT_DELAY)
    }
}