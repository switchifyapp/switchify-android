package com.enaboapps.switchify.service.gestures

import org.junit.After
import org.junit.Assert.assertFalse
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
    fun setRepeatOnTurnsRearmOff() {
        rearmEnabled = true

        val state = GestureModePolicy.setRepeatEnabledForTesting(true)

        assertTrue(state.repeatEnabled)
        assertFalse(state.rearmEnabled)
        assertTrue(repeatEnabled)
        assertFalse(rearmEnabled)
    }

    @Test
    fun setRearmOnTurnsRepeatOff() {
        repeatEnabled = true

        val state = GestureModePolicy.setRearmEnabledForTesting(true)

        assertFalse(state.repeatEnabled)
        assertTrue(state.rearmEnabled)
        assertFalse(repeatEnabled)
        assertTrue(rearmEnabled)
    }

    @Test
    fun setRepeatOffDoesNotTurnRearmOn() {
        GestureModePolicy.setRepeatEnabledForTesting(false)

        assertFalse(repeatEnabled)
        assertFalse(rearmEnabled)
    }

    @Test
    fun setRearmOffDoesNotTurnRepeatOn() {
        GestureModePolicy.setRearmEnabledForTesting(false)

        assertFalse(repeatEnabled)
        assertFalse(rearmEnabled)
    }
}
