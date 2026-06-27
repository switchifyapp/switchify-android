package com.enaboapps.switchify.pc

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager

object PcMouseRepeatDefaults {
    const val DEFAULT_INTERVAL_MS = 250L
    const val MIN_INTERVAL_MS = 100L
    const val MAX_INTERVAL_MS = 2000L
    const val INTERVAL_STEP_MS = 50L
}

interface PcMouseRepeatSettings {
    fun isEnabled(): Boolean
    fun intervalMs(): Long
}

class PreferencePcMouseRepeatSettings internal constructor(
    private val getBooleanValue: (String, Boolean) -> Boolean,
    private val getLongValue: (String, Long) -> Long
) : PcMouseRepeatSettings {
    constructor(context: Context) : this(
        getBooleanValue = PreferenceManager(context)::getBooleanValue,
        getLongValue = PreferenceManager(context)::getLongValue
    )

    override fun isEnabled(): Boolean {
        return getBooleanValue(PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT, true)
    }

    override fun intervalMs(): Long {
        return getLongValue(
            PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT_INTERVAL,
            PcMouseRepeatDefaults.DEFAULT_INTERVAL_MS
        ).coerceIn(
            PcMouseRepeatDefaults.MIN_INTERVAL_MS,
            PcMouseRepeatDefaults.MAX_INTERVAL_MS
        )
    }
}
