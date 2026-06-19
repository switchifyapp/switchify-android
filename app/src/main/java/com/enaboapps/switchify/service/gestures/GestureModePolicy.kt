package com.enaboapps.switchify.service.gestures

import com.enaboapps.switchify.R

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
    fun currentState(
        repeatEnabled: Boolean,
        rearmEnabled: Boolean
    ): GestureModeState {
        return if (repeatEnabled && rearmEnabled) {
            GestureModeState(repeatEnabled = false, rearmEnabled = false)
        } else {
            GestureModeState(repeatEnabled, rearmEnabled)
        }
    }

    fun setRepeatEnabled(
        enabled: Boolean,
        currentRepeatEnabled: Boolean,
        currentRearmEnabled: Boolean,
        isGestureLockEnabled: Boolean
    ): GestureModeChangeResult {
        val currentState = currentState(currentRepeatEnabled, currentRearmEnabled)
        if (!enabled) {
            return GestureModeChangeResult(
                state = currentState.copy(repeatEnabled = false),
                changed = currentState.repeatEnabled
            )
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

        return GestureModeChangeResult(
            state = currentState.copy(repeatEnabled = true),
            changed = !currentState.repeatEnabled
        )
    }

    fun setRearmEnabled(
        enabled: Boolean,
        currentRepeatEnabled: Boolean,
        currentRearmEnabled: Boolean,
        isGestureLockEnabled: Boolean
    ): GestureModeChangeResult {
        val currentState = currentState(currentRepeatEnabled, currentRearmEnabled)
        if (!enabled) {
            return GestureModeChangeResult(
                state = currentState.copy(rearmEnabled = false),
                changed = currentState.rearmEnabled
            )
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

        return GestureModeChangeResult(
            state = currentState.copy(rearmEnabled = true),
            changed = !currentState.rearmEnabled
        )
    }
}
