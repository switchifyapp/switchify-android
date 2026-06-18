package com.enaboapps.switchify.service.gestures

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager

data class GestureModeState(
    val repeatEnabled: Boolean,
    val rearmEnabled: Boolean
)

object GestureModePolicy {
    private var repeatProviderForTesting: (() -> Boolean)? = null
    private var rearmProviderForTesting: (() -> Boolean)? = null
    private var repeatSetterForTesting: ((Boolean) -> Unit)? = null
    private var rearmSetterForTesting: ((Boolean) -> Unit)? = null

    fun normalize(context: Context): GestureModeState {
        return normalizeState(context)
    }

    fun setRepeatEnabled(context: Context, enabled: Boolean): GestureModeState {
        if (enabled) {
            writeRepeat(context, true)
            writeRearm(context, false)
        } else {
            writeRepeat(context, false)
        }
        return normalize(context)
    }

    fun setRearmEnabled(context: Context, enabled: Boolean): GestureModeState {
        if (enabled) {
            writeRearm(context, true)
            writeRepeat(context, false)
        } else {
            writeRearm(context, false)
        }
        return normalize(context)
    }

    fun isRepeatEnabled(context: Context): Boolean {
        return normalize(context).repeatEnabled
    }

    fun isRearmEnabled(context: Context): Boolean {
        return normalize(context).rearmEnabled
    }

    private fun normalizeState(context: Context?): GestureModeState {
        val repeatEnabled = readRepeat(context)
        val rearmEnabled = readRearm(context)
        if (repeatEnabled && rearmEnabled) {
            writeRepeat(context, false)
            writeRearm(context, false)
            return GestureModeState(repeatEnabled = false, rearmEnabled = false)
        }
        return GestureModeState(repeatEnabled, rearmEnabled)
    }

    private fun readRepeat(context: Context?): Boolean {
        repeatProviderForTesting?.let { return it() }
        return context?.let {
            PreferenceManager(it).getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_REPEAT,
                false
            )
        } ?: false
    }

    private fun readRearm(context: Context?): Boolean {
        rearmProviderForTesting?.let { return it() }
        return context?.let {
            PreferenceManager(it).getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_LOCK_AUTO_REENABLE,
                false
            )
        } ?: false
    }

    private fun writeRepeat(context: Context?, enabled: Boolean) {
        repeatSetterForTesting?.let {
            it(enabled)
            return
        }
        context?.let {
            PreferenceManager(it).setBooleanValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_REPEAT,
                enabled
            )
        }
    }

    private fun writeRearm(context: Context?, enabled: Boolean) {
        rearmSetterForTesting?.let {
            it(enabled)
            return
        }
        context?.let {
            PreferenceManager(it).setBooleanValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_LOCK_AUTO_REENABLE,
                enabled
            )
        }
    }

    internal fun normalizeForTesting(): GestureModeState {
        return normalizeState(null)
    }

    internal fun setRepeatEnabledForTesting(enabled: Boolean): GestureModeState {
        if (enabled) {
            writeRepeat(null, true)
            writeRearm(null, false)
        } else {
            writeRepeat(null, false)
        }
        return normalizeState(null)
    }

    internal fun setRearmEnabledForTesting(enabled: Boolean): GestureModeState {
        if (enabled) {
            writeRearm(null, true)
            writeRepeat(null, false)
        } else {
            writeRearm(null, false)
        }
        return normalizeState(null)
    }

    internal fun setPreferenceAccessorsForTesting(
        repeatProvider: (() -> Boolean)?,
        rearmProvider: (() -> Boolean)?,
        repeatSetter: ((Boolean) -> Unit)?,
        rearmSetter: ((Boolean) -> Unit)?
    ) {
        repeatProviderForTesting = repeatProvider
        rearmProviderForTesting = rearmProvider
        repeatSetterForTesting = repeatSetter
        rearmSetterForTesting = rearmSetter
    }

    internal fun resetForTesting() {
        repeatProviderForTesting = null
        rearmProviderForTesting = null
        repeatSetterForTesting = null
        rearmSetterForTesting = null
    }
}
