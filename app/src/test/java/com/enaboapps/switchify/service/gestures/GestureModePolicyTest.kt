package com.enaboapps.switchify.service.gestures

import com.enaboapps.switchify.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureModePolicyTest {
    private var repeatEnabled = false
    private var rearmEnabled = false

    @Before
    fun setup() {
        repeatEnabled = false
        rearmEnabled = false
        GestureModePolicy.setPreferenceAccessorsForTesting(
            repeatProvider = { repeatEnabled },
            rearmProvider = { rearmEnabled },
            repeatSetter = { repeatEnabled = it },
            rearmSetter = { rearmEnabled = it }
        )
    }

    @After
    fun tearDown() {
        GestureModePolicy.resetForTesting()
    }

    @Test
    fun normalizeLeavesBothOff() {
        val state = GestureModePolicy.normalizeForTesting()

        assertFalse(state.repeatEnabled)
        assertFalse(state.rearmEnabled)
        assertFalse(repeatEnabled)
        assertFalse(rearmEnabled)
    }

    @Test
    fun normalizeLeavesRepeatOnly() {
        repeatEnabled = true

        val state = GestureModePolicy.normalizeForTesting()

        assertTrue(state.repeatEnabled)
        assertFalse(state.rearmEnabled)
        assertTrue(repeatEnabled)
        assertFalse(rearmEnabled)
    }

    @Test
    fun normalizeLeavesRearmOnly() {
        rearmEnabled = true

        val state = GestureModePolicy.normalizeForTesting()

        assertFalse(state.repeatEnabled)
        assertTrue(state.rearmEnabled)
        assertFalse(repeatEnabled)
        assertTrue(rearmEnabled)
    }

    @Test
    fun normalizeTurnsBothOnToBothOff() {
        repeatEnabled = true
        rearmEnabled = true

        val state = GestureModePolicy.normalizeForTesting()

        assertFalse(state.repeatEnabled)
        assertFalse(state.rearmEnabled)
        assertFalse(repeatEnabled)
        assertFalse(rearmEnabled)
    }

    @Test
    fun setRepeatOnBlockedWhenRearmEnabled() {
        rearmEnabled = true

        val result = GestureModePolicy.setRepeatEnabledForTesting(true)

        assertFalse(result.changed)
        assertEquals(R.string.gesture_mode_blocked_rearm_enabled_for_repeat, result.blockedReasonResId)
        assertFalse(result.state.repeatEnabled)
        assertTrue(result.state.rearmEnabled)
        assertFalse(repeatEnabled)
        assertTrue(rearmEnabled)
    }

    @Test
    fun setRepeatOnBlockedWhenGestureLockEnabled() {
        val result = GestureModePolicy.setRepeatEnabledForTesting(
            true,
            isGestureLockEnabled = true
        )

        assertFalse(result.changed)
        assertEquals(R.string.gesture_mode_blocked_lock_enabled_for_repeat, result.blockedReasonResId)
        assertFalse(repeatEnabled)
        assertFalse(rearmEnabled)
    }

    @Test
    fun setRearmOnBlockedWhenRepeatEnabled() {
        repeatEnabled = true

        val result = GestureModePolicy.setRearmEnabledForTesting(true)

        assertFalse(result.changed)
        assertEquals(R.string.gesture_mode_blocked_repeat_enabled_for_rearm, result.blockedReasonResId)
        assertTrue(result.state.repeatEnabled)
        assertFalse(result.state.rearmEnabled)
        assertTrue(repeatEnabled)
        assertFalse(rearmEnabled)
    }

    @Test
    fun setRearmOnBlockedWhenGestureLockEnabled() {
        val result = GestureModePolicy.setRearmEnabledForTesting(
            true,
            isGestureLockEnabled = true
        )

        assertFalse(result.changed)
        assertEquals(R.string.gesture_mode_blocked_lock_enabled_for_rearm, result.blockedReasonResId)
        assertFalse(repeatEnabled)
        assertFalse(rearmEnabled)
    }

    @Test
    fun setRepeatOffAllowedWhenRearmEnabled() {
        repeatEnabled = true

        val result = GestureModePolicy.setRepeatEnabledForTesting(false)

        assertFalse(repeatEnabled)
        assertFalse(rearmEnabled)
        assertTrue(result.changed)
        assertNull(result.blockedReasonResId)
    }

    @Test
    fun setRearmOffAllowedWhenRepeatEnabled() {
        rearmEnabled = true

        val result = GestureModePolicy.setRearmEnabledForTesting(false)

        assertFalse(repeatEnabled)
        assertFalse(rearmEnabled)
        assertTrue(result.changed)
        assertNull(result.blockedReasonResId)
    }
}
