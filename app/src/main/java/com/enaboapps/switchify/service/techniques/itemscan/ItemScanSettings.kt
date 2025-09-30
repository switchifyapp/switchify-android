package com.enaboapps.switchify.service.techniques.itemscan

import android.content.Context
import android.content.Intent
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GestureManager

/**
 * Settings class for item scanning technique
 * Handles all item scan-specific configuration and preferences
 */
object ItemScanSettings {

    private var preferenceManager: PreferenceManager? = null

    const val ITEM_SCAN_SETTINGS_CHANGED_ACTION =
        "com.enaboapps.switchify.ITEM_SCAN_SETTINGS_CHANGED"

    fun init(context: Context) {
        preferenceManager = PreferenceManager(context)
    }

    private fun broadcastChanged(context: Context) {
        context.sendBroadcast(
            Intent(ITEM_SCAN_SETTINGS_CHANGED_ACTION).setPackage(context.packageName)
        )
    }

    /**
     * Check if item scan speech is enabled
     * @return true if item scan speech is enabled, false otherwise
     */
    fun isSpeechEnabled(): Boolean {
        return preferenceManager?.getBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_ITEM_SCAN_SPEECH,
            false
        ) ?: false
    }

    /**
     * Set item scan speech enabled
     * @param enabled Whether to enable speech
     * @param context Context for broadcasting changes
     */
    fun setSpeechEnabled(enabled: Boolean, context: Context) {
        preferenceManager?.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_ITEM_SCAN_SPEECH,
            enabled
        )
        broadcastChanged(context)
    }

    /**
     * Check if the automatically start scan after selection is enabled
     * @return true if automatically start scan after selection is enabled, false otherwise
     */
    fun isAutomaticallyStartScanAfterSelectionEnabled(): Boolean {
        val autoStartEnabled = preferenceManager?.getBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_AUTOMATICALLY_START_SCAN_AFTER_SELECTION
        ) ?: false

        // Only enable if auto scan mode and gesture lock is not enabled
        return autoStartEnabled &&
                isAutoScanMode() &&
                !GestureManager.instance.isGestureLockEnabled()
    }

    /**
     * Set automatically start scan after selection
     * @param enabled Whether to enable automatic scan restart
     * @param context Context for broadcasting changes
     */
    fun setAutomaticallyStartScanAfterSelectionEnabled(enabled: Boolean, context: Context) {
        preferenceManager?.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_AUTOMATICALLY_START_SCAN_AFTER_SELECTION,
            enabled
        )
        broadcastChanged(context)
    }

    /**
     * Check if the pause on first item is enabled
     * @return true if the pause on first item is enabled, false otherwise
     */
    fun isPauseOnFirstItemEnabled(): Boolean {
        return preferenceManager?.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM)
            ?: false
    }

    /**
     * Set pause on first item
     * @param enabled Whether to enable pause on first item
     * @param context Context for broadcasting changes
     */
    fun setPauseOnFirstItemEnabled(enabled: Boolean, context: Context) {
        preferenceManager?.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM,
            enabled
        )
        broadcastChanged(context)
    }

    /**
     * Get the pause on first item delay
     * @return The pause on first item delay if enabled, 0 otherwise
     */
    fun getPauseOnFirstItemDelay(): Long {
        return if (isPauseOnFirstItemEnabled()) {
            preferenceManager?.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM_DELAY)
                ?: 0L
        } else {
            0L
        }
    }

    /**
     * Set the pause on first item delay
     * @param delay The delay in milliseconds
     * @param context Context for broadcasting changes
     */
    fun setPauseOnFirstItemDelay(delay: Long, context: Context) {
        preferenceManager?.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM_DELAY,
            delay
        )
        broadcastChanged(context)
    }

    /**
     * Check if the auto select is enabled
     * @return true if the auto select is enabled, false otherwise
     */
    fun isAutoSelectEnabled(): Boolean {
        return preferenceManager?.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SELECT)
            ?: false
    }

    /**
     * Set auto select enabled
     * @param enabled Whether to enable auto select
     * @param context Context for broadcasting changes
     */
    fun setAutoSelectEnabled(enabled: Boolean, context: Context) {
        preferenceManager?.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SELECT,
            enabled
        )
        broadcastChanged(context)
    }

    /**
     * Get the auto select delay
     * @return The auto select delay if enabled, 0 otherwise
     */
    fun getAutoSelectDelay(): Long {
        return if (isAutoSelectEnabled()) {
            preferenceManager?.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SELECT_DELAY)
                ?: 0L
        } else {
            0L
        }
    }

    /**
     * Set the auto select delay
     * @param delay The delay in milliseconds
     * @param context Context for broadcasting changes
     */
    fun setAutoSelectDelay(delay: Long, context: Context) {
        preferenceManager?.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SELECT_DELAY,
            delay
        )
        broadcastChanged(context)
    }

    /**
     * Check if directly select keyboard keys is enabled
     * @return true if directly select keyboard keys is enabled, false otherwise
     */
    fun isDirectlySelectKeyboardKeysEnabled(): Boolean {
        return preferenceManager?.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_DIRECTLY_SELECT_KEYBOARD_KEYS)
            ?: false
    }

    /**
     * Set directly select keyboard keys enabled
     * @param enabled Whether to enable direct keyboard key selection
     * @param context Context for broadcasting changes
     */
    fun setDirectlySelectKeyboardKeysEnabled(enabled: Boolean, context: Context) {
        preferenceManager?.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_DIRECTLY_SELECT_KEYBOARD_KEYS,
            enabled
        )
        broadcastChanged(context)
    }

    /**
     * Check if row column scan is enabled
     * @return true if row column scan is enabled, false otherwise
     */
    fun isRowColumnScanEnabled(): Boolean {
        return preferenceManager?.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_ROW_COLUMN_SCAN)
            ?: false
    }

    /**
     * Set row column scan enabled
     * @param enabled Whether to enable row column scan
     * @param context Context for broadcasting changes
     */
    fun setRowColumnScanEnabled(enabled: Boolean, context: Context) {
        preferenceManager?.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_ROW_COLUMN_SCAN,
            enabled
        )
        broadcastChanged(context)
    }

    /**
     * Check if group scan is enabled
     * @return true if group scan is enabled, false otherwise
     */
    fun isGroupScanEnabled(): Boolean {
        return preferenceManager?.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_GROUP_SCAN)
            ?: false
    }

    /**
     * Set group scan enabled
     * @param enabled Whether to enable group scan
     * @param context Context for broadcasting changes
     */
    fun setGroupScanEnabled(enabled: Boolean, context: Context) {
        preferenceManager?.setBooleanValue(
            PreferenceManager.Keys.PREFERENCE_KEY_GROUP_SCAN,
            enabled
        )
        broadcastChanged(context)
    }

    // Helper method to check scan mode - could be moved to a shared utility if needed
    private fun isAutoScanMode(): Boolean {
        val scanModeId =
            preferenceManager?.getStringValue(PreferenceManager.Keys.PREFERENCE_KEY_SCAN_MODE) ?: ""
        return scanModeId == "auto"
    }
}