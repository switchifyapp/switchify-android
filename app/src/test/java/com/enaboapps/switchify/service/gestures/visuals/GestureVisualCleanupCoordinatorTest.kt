package com.enaboapps.switchify.service.gestures.visuals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GestureVisualCleanupCoordinatorTest {
    @Test
    fun normalCompletionCancelsWatchdogAndCleansOnce() {
        val scheduler = TestScheduler()
        var cleanupCount = 0
        val coordinator = scheduler.createCoordinator { cleanupCount++ }

        coordinator.schedule(1_750L)
        val watchdog = scheduler.pending
        coordinator.complete()
        watchdog?.run()

        assertEquals(1, cleanupCount)
        assertNull(scheduler.pending)
    }

    @Test
    fun watchdogCleansWhenAnimatorCompletionIsMissed() {
        val scheduler = TestScheduler()
        var cleanupCount = 0
        val coordinator = scheduler.createCoordinator { cleanupCount++ }

        coordinator.schedule(1_750L)
        scheduler.runPending()

        assertEquals(1, cleanupCount)
        assertNull(scheduler.pending)
    }

    @Test
    fun explicitCancellationAndRepeatedCompletionCleanOnce() {
        val scheduler = TestScheduler()
        var cleanupCount = 0
        val coordinator = scheduler.createCoordinator { cleanupCount++ }

        coordinator.schedule(1_750L)
        coordinator.complete()
        coordinator.complete()

        assertEquals(1, cleanupCount)
        assertNull(scheduler.pending)
    }

    @Test
    fun reschedulingReplacesPreviousWatchdog() {
        val scheduler = TestScheduler()
        var cleanupCount = 0
        val coordinator = scheduler.createCoordinator { cleanupCount++ }

        coordinator.schedule(500L)
        val first = scheduler.pending
        coordinator.schedule(750L)

        assertEquals(750L, scheduler.delayMs)
        assertSame(scheduler.pending, scheduler.lastPosted)
        first?.run()
        assertEquals(0, cleanupCount)
        assertSame(scheduler.pending, scheduler.lastPosted)
    }

    @Test
    fun deadlineUsesSystemScaleAndGrace() {
        assertEquals(
            1_750L,
            GestureVisualCleanupDeadline.calculate(1_500L, 1f, 250L)
        )
        assertEquals(
            3_250L,
            GestureVisualCleanupDeadline.calculate(1_500L, 2f, 250L)
        )
        assertEquals(
            250L,
            GestureVisualCleanupDeadline.calculate(-1L, -1f, 250L)
        )
    }

    private class TestScheduler {
        var pending: Runnable? = null
        var lastPosted: Runnable? = null
        var delayMs = 0L

        fun createCoordinator(cleanup: () -> Unit): GestureVisualCleanupCoordinator {
            return GestureVisualCleanupCoordinator(
                postDelayed = { runnable, delay ->
                    pending = runnable
                    lastPosted = runnable
                    delayMs = delay
                },
                removeCallbacks = { runnable ->
                    if (pending === runnable) pending = null
                },
                cleanup = cleanup
            )
        }

        fun runPending() {
            pending?.run()
        }
    }
}
