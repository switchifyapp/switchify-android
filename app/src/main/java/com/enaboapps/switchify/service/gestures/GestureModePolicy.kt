package com.enaboapps.switchify.service.gestures

import com.enaboapps.switchify.R

data class GestureModeState(
    val repeatEnabled: Boolean,
    val rearmEnabled: Boolean,
    val autoScrollActive: Boolean
)

data class GestureModeChangeResult(
    val state: GestureModeState,
    val changed: Boolean,
    val blockedReasonResId: Int? = null
)

object GestureModePolicy {
    fun currentState(
        repeatEnabled: Boolean,
        rearmEnabled: Boolean,
        autoScrollActive: Boolean
    ): GestureModeState {
        val activeCount = listOf(repeatEnabled, rearmEnabled, autoScrollActive).count { it }
        return if (activeCount > 1) {
            GestureModeState(
                repeatEnabled = false,
                rearmEnabled = false,
                autoScrollActive = false
            )
        } else {
            GestureModeState(repeatEnabled, rearmEnabled, autoScrollActive)
        }
    }

    fun setRepeatEnabled(
        enabled: Boolean,
        currentRepeatEnabled: Boolean,
        currentRearmEnabled: Boolean,
        isGestureLockEnabled: Boolean,
        isAutoScrollActive: Boolean
    ): GestureModeChangeResult {
        val currentState = currentState(
            currentRepeatEnabled,
            currentRearmEnabled,
            isAutoScrollActive
        )
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
        if (currentState.autoScrollActive) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_auto_scroll_enabled_for_repeat
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
        isGestureLockEnabled: Boolean,
        isAutoScrollActive: Boolean
    ): GestureModeChangeResult {
        val currentState = currentState(
            currentRepeatEnabled,
            currentRearmEnabled,
            isAutoScrollActive
        )
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
        if (currentState.autoScrollActive) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_auto_scroll_enabled_for_rearm
            )
        }

        return GestureModeChangeResult(
            state = currentState.copy(rearmEnabled = true),
            changed = !currentState.rearmEnabled
        )
    }

    fun canStartAutoScroll(
        currentRepeatEnabled: Boolean,
        currentRearmEnabled: Boolean,
        isGestureLockEnabled: Boolean
    ): GestureModeChangeResult {
        val currentState = currentState(
            currentRepeatEnabled,
            currentRearmEnabled,
            autoScrollActive = false
        )

        if (currentState.repeatEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_repeat_enabled_for_auto_scroll
            )
        }
        if (currentState.rearmEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_rearm_enabled_for_auto_scroll
            )
        }
        if (isGestureLockEnabled) {
            return GestureModeChangeResult(
                currentState,
                changed = false,
                blockedReasonResId = R.string.gesture_mode_blocked_lock_enabled_for_auto_scroll
            )
        }

        return GestureModeChangeResult(
            state = currentState.copy(autoScrollActive = true),
            changed = true
        )
    }
}
