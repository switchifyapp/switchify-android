package com.enaboapps.switchify.pc

import android.content.Context

class PcSwitchForwardingProfileStore(context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun rememberedProfileId(desktopId: String): String? =
        preferences.getString(desktopId, null)

    fun rememberProfile(desktopId: String, profileId: String) {
        preferences.edit().putString(desktopId, profileId).apply()
    }

    companion object {
        internal const val PREFERENCES_NAME = "pc_switch_forwarding_profiles"
    }
}

data class PcSwitchForwardingProfileSelection(
    val profile: PcSwitchProfileSummary?,
    val rememberedProfileUnavailable: Boolean
) {
    val fallbackProfileName: String?
        get() = profile?.name?.takeIf { rememberedProfileUnavailable }
}

fun selectPcSwitchForwardingProfile(
    profiles: List<PcSwitchProfileSummary>,
    rememberedProfileId: String?
): PcSwitchForwardingProfileSelection {
    val remembered = profiles.firstOrNull { it.id == rememberedProfileId }
    return PcSwitchForwardingProfileSelection(
        profile = remembered
            ?: profiles.firstOrNull { it.id == "builtin.keyboard" }
            ?: profiles.firstOrNull(),
        rememberedProfileUnavailable = rememberedProfileId != null && remembered == null
    )
}
