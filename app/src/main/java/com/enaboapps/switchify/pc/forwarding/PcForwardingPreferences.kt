package com.enaboapps.switchify.pc.forwarding

import android.content.Context
import androidx.core.content.edit
import com.enaboapps.switchify.pc.connection.PcConnectionState

data class PcForwardingRestoreIntent(val desktopId: String, val profileId: String, val profileVersion: Long)

class PcForwardingRestoreState {
    private var intent: PcForwardingRestoreIntent? = null

    fun get(): PcForwardingRestoreIntent? = intent

    fun set(value: PcForwardingRestoreIntent?) {
        intent = value
    }

    fun clear() {
        intent = null
    }

    companion object {
        fun shouldClear(connection: PcConnectionState): Boolean =
            connection !is PcConnectionState.Connected && connection !is PcConnectionState.Reconnecting
    }
}

interface PcForwardingPreferenceStore {
    fun holdToStopMs(): Long

    fun setHoldToStopMs(value: Long)

    fun rememberedProfileId(desktopId: String): String?

    fun rememberProfileId(desktopId: String, profileId: String)
}

object PcForwardingPreferences {
    val HOLD_TO_STOP_OPTIONS_MS = listOf(3_000L, 5_000L, 8_000L)

    fun normalizeHoldToStop(value: Long): Long =
        if (value in HOLD_TO_STOP_OPTIONS_MS) value else PcForwardingController.DEFAULT_HOLD_TO_STOP_MS
}

class SharedPreferencesPcForwardingPreferenceStore(context: Context) : PcForwardingPreferenceStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun holdToStopMs(): Long =
        PcForwardingPreferences.normalizeHoldToStop(
            preferences.getLong(HOLD_TO_STOP_KEY, PcForwardingController.DEFAULT_HOLD_TO_STOP_MS)
        )

    override fun setHoldToStopMs(value: Long) {
        preferences.edit { putLong(HOLD_TO_STOP_KEY, PcForwardingPreferences.normalizeHoldToStop(value)) }
    }

    override fun rememberedProfileId(desktopId: String): String? = preferences.getString(profileKey(desktopId), null)

    override fun rememberProfileId(desktopId: String, profileId: String) {
        preferences.edit { putString(profileKey(desktopId), profileId) }
    }

    private fun profileKey(desktopId: String) = "$PROFILE_KEY_PREFIX$desktopId"

    private companion object {
        const val FILE_NAME = "switchify_pc_forwarding"
        const val HOLD_TO_STOP_KEY = "holdToStopMs"
        const val PROFILE_KEY_PREFIX = "profile."
    }
}
