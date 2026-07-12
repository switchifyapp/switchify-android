package com.enaboapps.switchify.service.gestures.visuals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinearGestureVisualThrottleTest {
    @Test
    fun firstRequestDisplaysImmediately() {
        val scheduler = TestScheduler()
        val displayed = mutableListOf<String>()
        val throttle = scheduler.createThrottle(displayed)

        throttle.submit("first")

        assertEquals(listOf("first"), displayed)
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test
    fun requestsWithinWindowCoalesceToLatestAtTrailingEdge() {
        val scheduler = TestScheduler()
        val displayed = mutableListOf<String>()
        val throttle = scheduler.createThrottle(displayed)

        throttle.submit("first")
        scheduler.advanceBy(100)
        throttle.submit("second")
        scheduler.advanceBy(100)
        throttle.submit("latest")
        scheduler.advanceBy(299)

        assertEquals(listOf("first"), displayed)

        scheduler.advanceBy(1)

        assertEquals(listOf("first", "latest"), displayed)
    }

    @Test
    fun trailingDisplayStartsAnotherThrottleWindow() {
        val scheduler = TestScheduler()
        val displayed = mutableListOf<String>()
        val throttle = scheduler.createThrottle(displayed)

        throttle.submit("first")
        scheduler.advanceBy(250)
        throttle.submit("second")
        scheduler.advanceBy(250)
        scheduler.advanceBy(100)
        throttle.submit("third")
        scheduler.advanceBy(400)

        assertEquals(listOf("first", "second", "third"), displayed)
    }

    @Test
    fun clearCancelsPendingRequestAndResetsWindow() {
        val scheduler = TestScheduler()
        val displayed = mutableListOf<String>()
        val throttle = scheduler.createThrottle(displayed)

        throttle.submit("first")
        scheduler.advanceBy(100)
        throttle.submit("pending")
        throttle.clear()
        scheduler.advanceBy(400)
        throttle.submit("after-clear")

        assertEquals(listOf("first", "after-clear"), displayed)
        assertTrue(scheduler.pending.isEmpty())
    }

    private class TestScheduler {
        var now = 0L
        val pending = mutableListOf<Scheduled>()

        fun createThrottle(displayed: MutableList<String>) = LinearGestureVisualThrottle(
            intervalMs = 500L,
            currentTimeMs = { now },
            postDelayed = { runnable, delay -> pending.add(Scheduled(now + delay, runnable)) },
            removeCallbacks = { runnable -> pending.removeAll { it.runnable === runnable } },
            display = displayed::add
        )

        fun advanceBy(durationMs: Long) {
            val target = now + durationMs
            while (true) {
                val next = pending.minByOrNull { it.timeMs } ?: break
                if (next.timeMs > target) break
                pending.remove(next)
                now = next.timeMs
                next.runnable.run()
            }
            now = target
        }

        data class Scheduled(val timeMs: Long, val runnable: Runnable)
    }
}
