package com.enaboapps.switchify.backend.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

class PreferenceManager(context: Context) {

    companion object Keys {
        const val PREFERENCE_KEY_SETUP_COMPLETE = "setup_complete"
        const val PREFERENCE_KEY_PRO = "pro"
        const val PREFERENCE_KEY_SCAN_MODE = "scan_mode"
        const val PREFERENCE_KEY_SCAN_RATE = "scan_rate"
        const val PREFERENCE_KEY_SCAN_CYCLES = "scan_cycles"
        const val PREFERENCE_KEY_ACCESS_TECHNIQUE = "access_technique"
        const val PREFERENCE_KEY_POINT_SCAN_LINE_SPEED_LEVEL = "point_scan_line_speed_level"
        const val PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE = "cursor_block_scan_rate"
        const val PREFERENCE_KEY_CURSOR_BLOCK_COUNT = "cursor_block_count"
        const val PREFERENCE_KEY_RADAR_SPEED_LEVEL = "radar_speed_level"
        const val PREFERENCE_KEY_RADAR_SLOW_DOWN_THEN_SELECT = "radar_slow_down_then_select"
        const val PREFERENCE_KEY_RADAR_STARTING_POSITION = "radar_starting_position"
        const val PREFERENCE_KEY_SWITCH_HOLD_TIME = "switch_hold_time"
        const val PREFERENCE_KEY_HOLD_TO_UNPAUSE_DURATION = "hold_to_unpause_duration"
        const val PREFERENCE_KEY_PAUSE_TIMEOUT = "pause_timeout"
        const val PREFERENCE_KEY_MOVE_REPEAT = "move_repeat"
        const val PREFERENCE_KEY_MOVE_REPEAT_DELAY = "move_repeat_delay"
        const val PREFERENCE_KEY_PC_MOUSE_MOVEMENT_SIZE = "pc_mouse_movement_size"
        const val PREFERENCE_KEY_PC_CONTROL_SURFACE = "pc_control_surface"
        const val PREFERENCE_KEY_PC_MOUSE_REPEAT = "pc_mouse_repeat"
        const val PREFERENCE_KEY_PC_MOUSE_REPEAT_INTERVAL = "pc_mouse_repeat_interval"
        const val PREFERENCE_KEY_AUTOMATICALLY_START_SCAN_AFTER_SELECTION =
            "automatically_start_scan_after_selection"
        const val PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM = "pause_on_first_item"
        const val PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM_DELAY = "pause_on_first_item_delay"
        const val PREFERENCE_KEY_GROUP_SCAN = "group_scan"
        const val PREFERENCE_KEY_AUTO_SELECT = "auto_select"
        const val PREFERENCE_KEY_DIRECTLY_SELECT_KEYBOARD_KEYS = "directly_select_keyboard_keys"
        const val PREFERENCE_KEY_AUTO_SELECT_DELAY = "auto_select_delay"
        const val PREFERENCE_KEY_ASSISTED_SELECTION = "assisted_selection"
        const val PREFERENCE_KEY_CURSOR_MODE = "cursor_mode"
        const val PREFERENCE_KEY_ROW_COLUMN_SCAN = "row_column_scan"
        const val PREFERENCE_KEY_ITEM_SCAN_SPEECH = "item_scan_speech"
        const val PREFERENCE_KEY_SWITCH_IGNORE_REPEAT = "switch_ignore_repeat"
        const val PREFERENCE_KEY_SWITCH_IGNORE_REPEAT_DELAY = "switch_ignore_repeat_delay"
        const val PREFERENCE_KEY_AUTO_SCROLL = "auto_scroll"
        const val PREFERENCE_KEY_AUTO_SCROLL_DELAY = "auto_scroll_delay"
        const val PREFERENCE_KEY_STOP_PATTERN_ON_SWITCH = "stop_pattern_on_switch"
        const val PREFERENCE_KEY_GESTURE_PATTERN_MANUAL_PROGRESSION = "gesture_pattern_manual_progression"
        const val PREFERENCE_KEY_GESTURE_LOCK = "gesture_lock"
        const val PREFERENCE_KEY_GESTURE_LOCK_AUTO_REENABLE = "gesture_lock_auto_reenable"
        const val PREFERENCE_KEY_GESTURE_REPEAT = "gesture_repeat"
        const val PREFERENCE_KEY_GESTURE_REPEAT_INITIAL_DELAY = "gesture_repeat_initial_delay"
        const val PREFERENCE_KEY_GESTURE_REPEAT_DELAY = "gesture_repeat_delay"
        const val PREFERENCE_KEY_SCAN_COLOR_SET = "scan_color_set"
        const val PREFERENCE_KEY_SCAN_HIGHLIGHT_TYPE = "scan_highlight_type"
        const val PREFERENCE_KEY_MENU_TRANSPARENCY = "menu_transparency"
        const val PREFERENCE_KEY_MENU_SIZE_SCALE = "menu_size_scale"
        const val PREFERENCE_KEY_SETTINGS_TAB = "settings_tab"
        const val PREFERENCE_KEY_TELEMETRY_ENABLED = "telemetry_enabled"
        const val PREFERENCE_KEY_DEVICE_ID = "device_id"
        const val PREFERENCE_KEY_ONBOARDING_CURRENT_STEP = "onboarding_current_step"
        const val PREFERENCE_KEY_ONBOARDING_USER_TYPE = "onboarding_user_type"
        const val PREFERENCE_KEY_ONBOARDING_IS_NEW_USER = "onboarding_is_new_user"
        const val PREFERENCE_KEY_REVIEW_LAST_SHOWN = "review_last_shown"
        const val PREFERENCE_KEY_DEBUG_TRIAL_DISABLED = "debug_trial_disabled"

        // Pro reminder engagement preferences
        const val PREFERENCE_KEY_PRO_REMINDER_USAGE_DAYS = "pro_reminder_usage_days"
        const val PREFERENCE_KEY_PRO_REMINDER_LAST_USAGE_DATE = "pro_reminder_last_usage_date"
        const val PREFERENCE_KEY_PRO_REMINDER_DISMISS_COUNT = "pro_reminder_dismiss_count"
        const val PREFERENCE_KEY_PRO_REMINDER_LAST_DISMISS_TIME = "pro_reminder_last_dismiss_time"
        const val PREFERENCE_KEY_PRO_REMINDER_COOLDOWN_DAYS = "pro_reminder_cooldown_days"

        // Favourite apps
        const val PREFERENCE_KEY_FAVOURITE_APPS = "favourite_apps"

        // Camera threshold preferences - time steppers for each gesture
        const val PREFERENCE_KEY_CAMERA_SMILE_TIME = "camera_smile_time"
        const val PREFERENCE_KEY_CAMERA_LEFT_WINK_TIME = "camera_left_wink_time"
        const val PREFERENCE_KEY_CAMERA_RIGHT_WINK_TIME = "camera_right_wink_time"
        const val PREFERENCE_KEY_CAMERA_BLINK_TIME = "camera_blink_time"
        const val PREFERENCE_KEY_CAMERA_PUCKER_TIME = "camera_pucker_time"
        const val PREFERENCE_KEY_CAMERA_HEAD_TURN_LEFT_SENSITIVITY =
            "camera_head_turn_left_sensitivity"
        const val PREFERENCE_KEY_CAMERA_HEAD_TURN_RIGHT_SENSITIVITY =
            "camera_head_turn_right_sensitivity"
        const val PREFERENCE_KEY_CAMERA_HEAD_TURN_UP_SENSITIVITY = "camera_head_turn_up_sensitivity"
        const val PREFERENCE_KEY_CAMERA_HEAD_TURN_DOWN_SENSITIVITY =
            "camera_head_turn_down_sensitivity"

        // Gemma terms acceptance for the on-device AI model
        const val PREFERENCE_KEY_GEMMA_TERMS_ACCEPTED =
            "reply_drafter_gemma_terms_accepted"

        private const val PREFERENCE_FILE_NAME = "switchify_preferences"

        /**
         * Keys that should NOT be synced across devices - single source of truth
         */
        val BLACKLISTED_KEYS = setOf(
            PREFERENCE_KEY_PRO,
            PREFERENCE_KEY_ACCESS_TECHNIQUE,
            PREFERENCE_KEY_SETUP_COMPLETE,
            PREFERENCE_KEY_REVIEW_LAST_SHOWN,
            PREFERENCE_KEY_TELEMETRY_ENABLED,
            PREFERENCE_KEY_DEVICE_ID,
            PREFERENCE_KEY_GEMMA_TERMS_ACCEPTED
        )
    }

    private val appContext = context.createDeviceProtectedStorageContext()
    private val defaultContext = context
    private val sharedPreferences: SharedPreferences =
        appContext.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)

    fun migrateToProtectedStorage() {
        val defaultPrefs =
            defaultContext.getSharedPreferences(PREFERENCE_FILE_NAME, Context.MODE_PRIVATE)
        // Skip if there's nothing to migrate
        if (defaultPrefs.all.isEmpty()) return

        sharedPreferences.edit {
            defaultPrefs.all.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
        // Clear the old preferences after successful migration
        defaultPrefs.edit { clear() }
    }

    val preferenceSync = PreferenceSync.getInstance()
    private val syncQueue = SyncQueue.getInstance()

    fun enableSync() {
        preferenceSync.initialize(sharedPreferences)
    }

    /**
     * Forces immediate sync of all pending changes without delay.
     * Useful for scenarios like logout where you want to ensure all changes are synced.
     */
    fun forceSyncNow() {
        syncQueue.forceSyncNow()
    }

    /**
     * Clears the sync queue without uploading.
     * Useful for logout scenarios.
     */
    fun clearSyncQueue() {
        syncQueue.clearQueue()
    }

    fun setSetupComplete() {
        setBooleanValue(PREFERENCE_KEY_SETUP_COMPLETE, true)
    }

    fun isSetupComplete(): Boolean {
        return getBooleanValue(PREFERENCE_KEY_SETUP_COMPLETE)
    }

    /**
     * Sets whether the user has opted in to sending telemetry (analytics events and
     * crash reports) to the Timberlogs ingest worker. Device-local; not synced.
     */
    fun setTelemetryEnabled(enabled: Boolean) {
        setBooleanValue(PREFERENCE_KEY_TELEMETRY_ENABLED, enabled)
    }

    /**
     * Returns whether telemetry is enabled. Defaults to false (opt-in): both brand-new
     * installs and existing installs upgrading over this change start with telemetry off
     * until the user explicitly enables it.
     */
    fun isTelemetryEnabled(): Boolean {
        return getBooleanValue(PREFERENCE_KEY_TELEMETRY_ENABLED, false)
    }

    fun setIntegerValue(key: String, value: Int) {
        sharedPreferences.edit {
            putInt(key, value)
        }
        syncQueue.queueChange(key, value)
    }

    fun setFloatValue(key: String, value: Float) {
        sharedPreferences.edit {
            putFloat(key, value)
        }
        syncQueue.queueChange(key, value)
    }

    fun setBooleanValue(key: String, value: Boolean) {
        sharedPreferences.edit {
            putBoolean(key, value)
        }
        syncQueue.queueChange(key, value)
    }

    fun setLongValue(key: String, value: Long) {
        sharedPreferences.edit {
            putLong(key, value)
        }
        syncQueue.queueChange(key, value)
    }

    fun setStringValue(key: String, value: String) {
        sharedPreferences.edit {
            putString(key, value)
        }
        syncQueue.queueChange(key, value)
    }

    fun getFloatValue(key: String, defaultValue: Float = 0f): Float {
        return sharedPreferences.getFloat(key, defaultValue)
    }

    fun getBooleanValue(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun getIntegerValue(key: String, defaultValue: Int = 1000): Int {
        // Handle type mismatches gracefully
        return try {
            sharedPreferences.getInt(key, defaultValue)
        } catch (e: ClassCastException) {
            // Try to get as Long and convert to Int
            try {
                sharedPreferences.getLong(key, defaultValue.toLong()).toInt()
            } catch (e: ClassCastException) {
                defaultValue
            }
        }
    }

    fun getLongValue(key: String, defaultValue: Long = 1000L): Long {
        // Due to an old version of the app storing some values as different types, we need to do try/catch
        return try {
            sharedPreferences.getLong(key, defaultValue)
        } catch (e: ClassCastException) {
            defaultValue
        }
    }

    fun getStringValue(key: String, defaultValue: String = ""): String {
        // Due to an old version of the app storing some values as different types, we need to do try/catch
        return try {
            sharedPreferences.getString(key, defaultValue) ?: defaultValue
        } catch (e: ClassCastException) {
            defaultValue
        }
    }

    fun getOrCreateDeviceId(): String {
        val existing = getStringValue(PREFERENCE_KEY_DEVICE_ID)
        if (existing.isNotEmpty()) return existing
        val generated = UUID.randomUUID().toString()
        setStringValue(PREFERENCE_KEY_DEVICE_ID, generated)
        return generated
    }

    /**
     * Clears all whitelisted preferences (synced preferences) while preserving blacklisted keys.
     * Useful for logout/account deletion when user wants to clear their local data.
     */
    fun clearWhitelistedPreferences() {
        sharedPreferences.edit {
            // Get all current preferences
            val allPrefs = sharedPreferences.all

            // Remove all except blacklisted keys
            allPrefs.keys.forEach { key ->
                if (!BLACKLISTED_KEYS.contains(key)) {
                    remove(key)
                }
            }
        }

        // Clear sync queue to prevent syncing cleared preferences
        clearSyncQueue()
    }
}
