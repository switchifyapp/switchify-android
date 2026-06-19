package com.enaboapps.switchify.service.gestures

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager

data class GestureModeState(
    val repeatEnabled: Boolean,
    val rearmEnabled: Boolean
)

data class GestureModeChangeResult(
    val state: GestureModeState,
    val changed: Boolean,
    val blockedReasonResId: Int? = null
)

object GestureModePolicy {
    private var repeatProviderForTesting: (() -> Boolean)? = null
    private var rearmProviderForTesting: (() -> Boolean)? = null
    private var repeatSetterForTesting: ((Boolean) -> Unit)? = null
    private var rearmSetterForTesting: ((Boolean) -> Unit)? = null

    fun normalize(context: Context): GestureModeState {
        return normalizeState(context)
    }

    fun setRepeatEnabled(
        context: Context,
        enabled: Boolean,
        isGestureLockEnabled: Boolean
    ): GestureModeChangeResult {
        val currentState = normalize(context)
        if (!enabled) {
            writeRepeat(context, false)
            return GestureModeChangeResult(normalize(context), changed = currentState.repeatEnabled)
        }

        if (currentState.rearmEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_rearm_enabled_for_repeat
            )
        }
        if (isGestureLockEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_lock_enabled_for_repeat
            )
        }

        writeRepeat(context, true)
        return GestureModeChangeResult(normalize(context), changed = !currentState.repeatEnabled)
    }

    fun setRearmEnabled(
        context: Context,
        enabled: Boolean,
        isGestureLockEnabled: Boolean
    ): GestureModeChangeResult {
        val currentState = normalize(context)
        if (!enabled) {
            writeRearm(context, false)
            return GestureModeChangeResult(normalize(context), changed = currentState.rearmEnabled)
        }

        if (currentState.repeatEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_repeat_enabled_for_rearm
            )
        }
        if (isGestureLockEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_lock_enabled_for_rearm
            )
        }

        writeRearm(context, true)
        return GestureModeChangeResult(normalize(context), changed = !currentState.rearmEnabled)
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

    internal fun setRepeatEnabledForTesting(
        enabled: Boolean,
        isGestureLockEnabled: Boolean = false
    ): GestureModeChangeResult {
        val currentState = normalizeState(null)
        if (!enabled) {
            writeRepeat(null, false)
            return GestureModeChangeResult(normalizeState(null), changed = currentState.repeatEnabled)
        }

        if (currentState.rearmEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_rearm_enabled_for_repeat
            )
        }
        if (isGestureLockEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_lock_enabled_for_repeat
            )
        }

        writeRepeat(null, true)
        return GestureModeChangeResult(normalizeState(null), changed = !currentState.repeatEnabled)
    }

    internal fun setRearmEnabledForTesting(
        enabled: Boolean,
        isGestureLockEnabled: Boolean = false
    ): GestureModeChangeResult {
        val currentState = normalizeState(null)
        if (!enabled) {
            writeRearm(null, false)
            return GestureModeChangeResult(normalizeState(null), changed = currentState.rearmEnabled)
        }

        if (currentState.repeatEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_repeat_enabled_for_rearm
            )
        }
        if (isGestureLockEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_lock_enabled_for_rearm
            )
        }

        writeRearm(null, true)
        return GestureModeChangeResult(normalizeState(null), changed = !currentState.rearmEnabled)
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
