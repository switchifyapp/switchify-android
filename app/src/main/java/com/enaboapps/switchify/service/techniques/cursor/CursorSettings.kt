package com.enaboapps.switchify.service.techniques.cursor

import android.content.Context
import android.content.Intent
import com.enaboapps.switchify.backend.preferences.PreferenceManager

object CursorSettings {

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
            println("Stored mode: $storedMode")
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
            Modes.MODE_SINGLE -> "Single"
            Modes.MODE_BLOCK -> "Block"
            else -> "Unknown"
        }
    }

    fun getModeDescription(mode: String): String {
        return when (mode) {
            Modes.MODE_SINGLE -> "Use a single line to select a point on the screen"
            Modes.MODE_BLOCK -> "Use blocks to first select a region and then use a single line to select a point in the region"
            else -> "Unknown"
        }
    }

    /**
     * Get the cursor scan rate
     * @return The cursor scan rate
     */
    fun getCursorBlockScanRate(): Long {
        return preferenceManager?.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE)
            ?: 1000L
    }

    /**
     * Get the cursor block count
     * @return The cursor block count
     */
    fun getCursorBlockCount(): Int {
        return preferenceManager?.getStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
            "4"
        )?.toInt() ?: 4
    }

    /**
     * Get the fine cursor scan rate
     * @return The fine cursor scan rate
     */
    fun getFineCursorScanRate(): Long {
        return preferenceManager?.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_FINE_SCAN_RATE)
            ?: 1000L
    }

    /**
     * Set the cursor block count
     * @param count The cursor block count
     */
    fun setCursorBlockCount(count: Int, context: Context) {
        preferenceManager?.setStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
            count.toString()
        )
        broadcastChanged(context)
    }

    /**
     * Set the cursor block scan rate
     * @param rate The cursor block scan rate
     */
    fun setCursorBlockScanRate(rate: Long, context: Context) {
        preferenceManager?.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE,
            rate
        )
        broadcastChanged(context)
    }

    /**
     * Set the fine cursor scan rate
     * @param rate The fine cursor scan rate
     */
    fun setFineCursorScanRate(rate: Long, context: Context) {
        preferenceManager?.setLongValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_FINE_SCAN_RATE,
            rate
        )
        broadcastChanged(context)
    }
}