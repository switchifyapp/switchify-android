package com.enaboapps.switchify.service.switches.external

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseSwitchHoldTrackerTest {
    @Test
    fun releaseBeforeHoldDurationReturnsFalse() {
        val tracker = PauseSwitchHoldTracker()

        tracker.onPressed(keyCode = 10, now = 1000L)

        assertFalse(
            tracker.consumeRelease(
                keyCode = 10,
                now = 1500L,
                holdDuration = 1000L
            )
        )
    }

    @Test
    fun releaseAfterHoldDurationReturnsTrue() {
        val tracker = PauseSwitchHoldTracker()

        tracker.onPressed(keyCode = 10, now = 1000L)

        assertTrue(
            tracker.consumeRelease(
                keyCode = 10,
                now = 2000L,
                holdDuration = 1000L
            )
        )
    }

    @Test
    fun repeatedPressForSameKeyDoesNotResetHoldTimer() {
        val tracker = PauseSwitchHoldTracker()

        tracker.onPressed(keyCode = 10, now = 1000L)
        tracker.onPressed(keyCode = 10, now = 1900L)

        assertTrue(
            tracker.consumeRelease(
                keyCode = 10,
                now = 2000L,
                holdDuration = 1000L
            )
        )
    }

    @Test
    fun differentKeyPressReplacesTrackedKey() {
        val tracker = PauseSwitchHoldTracker()

        tracker.onPressed(keyCode = 10, now = 1000L)
        tracker.onPressed(keyCode = 11, now = 1900L)

        assertFalse(
            tracker.consumeRelease(
                keyCode = 10,
                now = 2000L,
                holdDuration = 1000L
            )
        )
    }

    @Test
    fun releaseConsumesAndClearsState() {
        val tracker = PauseSwitchHoldTracker()

        tracker.onPressed(keyCode = 10, now = 1000L)
        assertTrue(tracker.consumeRelease(keyCode = 10, now = 2000L, holdDuration = 1000L))

        assertFalse(tracker.consumeRelease(keyCode = 10, now = 3000L, holdDuration = 1000L))
    }

    @Test
    fun resetClearsState() {
        val tracker = PauseSwitchHoldTracker()

        tracker.onPressed(keyCode = 10, now = 1000L)
        tracker.reset()

        assertFalse(tracker.consumeRelease(keyCode = 10, now = 2000L, holdDuration = 1000L))
    }
}
