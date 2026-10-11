package com.enaboapps.switchify.pc.storage

import android.content.Context
import androidx.core.content.edit

object LegacyPcDataCleanup {
    val LEGACY_PREFERENCE_FILES = listOf("switchify_pc_pairings", "pc_switch_forwarding_profiles")

    val LEGACY_PREFERENCE_KEYS = setOf(
        "pc_control_surface",
        "pc_typing_draft",
        "pc_typing_mode",
        "pc_mouse_repeat",
        "pc_mouse_repeat_interval",
        "pc_switch_forwarding_hold_to_stop_duration"
    )

    private const val SETTINGS_FILE = "switchify_preferences"

    fun run(context: Context) {
        val storage = context.applicationContext.createDeviceProtectedStorageContext()
        LEGACY_PREFERENCE_FILES.forEach { storage.deleteSharedPreferences(it) }
        val settings = storage.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
        val stale = LEGACY_PREFERENCE_KEYS.filter { settings.contains(it) }
        if (stale.isNotEmpty()) settings.edit { stale.forEach { remove(it) } }
    }
}
