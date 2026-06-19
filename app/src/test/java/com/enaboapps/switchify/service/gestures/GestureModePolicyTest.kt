package com.enaboapps.switchify.service.gestures

import com.enaboapps.switchify.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureModePolicyTest {
    @Test
    fun currentStateLeavesBothOff() {
        val state = GestureModePolicy.currentState(
            repeatEnabled = false,
            rearmEnabled = false
        )

        assertFalse(state.repeatEnabled)
        assertFalse(state.rearmEnabled)
    }

    @Test
    fun currentStateLeavesRepeatOnly() {
        val state = GestureModePolicy.currentState(
            repeatEnabled = true,
            rearmEnabled = false
        )

        assertTrue(state.repeatEnabled)
        assertFalse(state.rearmEnabled)
    }

    @Test
    fun currentStateLeavesRearmOnly() {
        val state = GestureModePolicy.currentState(
            repeatEnabled = false,
            rearmEnabled = true
        )

        assertFalse(state.repeatEnabled)
        assertTrue(state.rearmEnabled)
    }

    @Test
    fun currentStateNormalizesBothOnToBothOff() {
        val state = GestureModePolicy.currentState(
            repeatEnabled = true,
            rearmEnabled = true
        )

        assertFalse(state.repeatEnabled)
        assertFalse(state.rearmEnabled)
    }

    @Test
    fun setRepeatOnBlockedWhenRearmEnabled() {
        val result = GestureModePolicy.setRepeatEnabled(
            enabled = true,
            currentRepeatEnabled = false,
            currentRearmEnabled = true,
            isGestureLockEnabled = false
        )

        assertFalse(result.changed)
        assertEquals(R.string.gesture_mode_blocked_rearm_enabled_for_repeat, result.blockedReasonResId)
        assertFalse(result.state.repeatEnabled)
        assertTrue(result.state.rearmEnabled)
    }

    @Test
    fun setRepeatOnBlockedWhenGestureLockEnabled() {
        val result = GestureModePolicy.setRepeatEnabled(
            enabled = true,
            currentRepeatEnabled = false,
            currentRearmEnabled = false,
            isGestureLockEnabled = true
        )

        assertFalse(result.changed)
        assertEquals(R.string.gesture_mode_blocked_lock_enabled_for_repeat, result.blockedReasonResId)
        assertFalse(result.state.repeatEnabled)
        assertFalse(result.state.rearmEnabled)
    }

    @Test
    fun setRearmOnBlockedWhenRepeatEnabled() {
        val result = GestureModePolicy.setRearmEnabled(
            enabled = true,
            currentRepeatEnabled = true,
            currentRearmEnabled = false,
            isGestureLockEnabled = false
        )

        assertFalse(result.changed)
        assertEquals(R.string.gesture_mode_blocked_repeat_enabled_for_rearm, result.blockedReasonResId)
        assertTrue(result.state.repeatEnabled)
        assertFalse(result.state.rearmEnabled)
    }

    @Test
    fun setRearmOnBlockedWhenGestureLockEnabled() {
        val result = GestureModePolicy.setRearmEnabled(
            enabled = true,
            currentRepeatEnabled = false,
            currentRearmEnabled = false,
            isGestureLockEnabled = true
        )

        assertFalse(result.changed)
        assertEquals(R.string.gesture_mode_blocked_lock_enabled_for_rearm, result.blockedReasonResId)
        assertFalse(result.state.repeatEnabled)
        assertFalse(result.state.rearmEnabled)
    }

    @Test
    fun setRepeatOffAllowedWhenRearmEnabled() {
        val result = GestureModePolicy.setRepeatEnabled(
            enabled = false,
            currentRepeatEnabled = false,
            currentRearmEnabled = true,
            isGestureLockEnabled = false
        )

        assertFalse(result.changed)
        assertNull(result.blockedReasonResId)
        assertFalse(result.state.repeatEnabled)
        assertTrue(result.state.rearmEnabled)
    }

    @Test
    fun setRearmOffAllowedWhenRepeatEnabled() {
        val result = GestureModePolicy.setRearmEnabled(
            enabled = false,
            currentRepeatEnabled = true,
            currentRearmEnabled = false,
            isGestureLockEnabled = false
        )

        assertFalse(result.changed)
        assertNull(result.blockedReasonResId)
        assertTrue(result.state.repeatEnabled)
        assertFalse(result.state.rearmEnabled)
    }
}
