package com.enaboapps.switchify.switches

import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchActionTest {
    @Test
    fun actionsIncludesToggleGestureLockRearm() {
        val ids = SwitchAction.actions.map { it.id }

        assertTrue(ids.contains(SwitchAction.ACTION_TOGGLE_GESTURE_LOCK_REARM))
    }

    @Test
    fun actionsIncludesToggleGestureRepeat() {
        val ids = SwitchAction.actions.map { it.id }

        assertTrue(ids.contains(SwitchAction.ACTION_TOGGLE_GESTURE_REPEAT))
    }

    @Test
    fun toggleGestureLockRearmUsesStableAppendedId() {
        assertTrue(SwitchAction.ACTION_TOGGLE_GESTURE_LOCK_REARM == 15)
    }

    @Test
    fun toggleGestureRepeatUsesStableAppendedId() {
        assertTrue(SwitchAction.ACTION_TOGGLE_GESTURE_REPEAT == 16)
    }
}
