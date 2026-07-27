package com.enaboapps.switchify.pc

import android.content.Context

class PcSwitchProfilePreferenceStore(context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun rememberedProfileId(desktopId: String): String? =
        preferences.getString(desktopId, null)

    fun rememberProfile(desktopId: String, profileId: String) {
        preferences.edit().putString(desktopId, profileId).apply()
    }

    companion object {
        private const val FILE_NAME = "pc_switch_control_profiles"
    }
}

data class PcSwitchProfileSelection(
    val profile: PcSwitchProfileSummary?,
    val rememberedProfileUnavailable: Boolean
)

fun selectPcSwitchProfile(
    profiles: List<PcSwitchProfileSummary>,
    rememberedProfileId: String?
): PcSwitchProfileSelection {
    val remembered = profiles.firstOrNull { it.id == rememberedProfileId }
    return PcSwitchProfileSelection(
        profile = remembered
            ?: profiles.firstOrNull { it.id == "builtin.keyboard" }
            ?: profiles.firstOrNull(),
        rememberedProfileUnavailable = rememberedProfileId != null && remembered == null
    )
}
