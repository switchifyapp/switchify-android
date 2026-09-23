package com.enaboapps.switchify.service.core

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityEventPipelineTest {
    @Test
    fun burstsCoalesceDuringMinimumRefreshInterval() = runTest {
        var calls = 0
        val pipeline = AccessibilityEventPipeline(this,
            eventDispatcher = StandardTestDispatcher(testScheduler),
            minimumRefreshIntervalMs = 50L) { calls++ }
        pipeline.start()
        pipeline.requestRefresh()
        runCurrent()
        repeat(100) { pipeline.requestRefresh() }
        advanceTimeBy(49)
        assertEquals(1, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, calls)
        pipeline.stop()
    }

    @Test
    fun delayedAndExplicitRefreshesNeverOverlap() = runTest {
        var active = 0
        var peak = 0
        var completed = 0
        val pipeline = AccessibilityEventPipeline(this, 10L, StandardTestDispatcher(testScheduler)) {
            active++
            peak = maxOf(peak, active)
            delay(20)
            completed++
            active--
        }
        pipeline.start()
        pipeline.trySendEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        runCurrent()
        repeat(100) { pipeline.requestRefresh() }
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, peak)
        assertEquals(3, completed)
        pipeline.stop()
    }

    @Test
    fun windowTransitionInvalidatesInFlightResults() = runTest {
        val published = mutableListOf<Int>()
        var pass = 0
        val pipeline = AccessibilityEventPipeline(this, 100L, StandardTestDispatcher(testScheduler)) { current ->
            val id = ++pass
            delay(10)
            if (current()) published.add(id)
        }
        pipeline.start()
        pipeline.requestRefresh()
        runCurrent()
        pipeline.trySendEventType(AccessibilityEvent.TYPE_WINDOWS_CHANGED)
        advanceTimeBy(25)
        runCurrent()
        assertEquals(listOf(2), published)
        pipeline.stop()
    }

    @Test
    fun sleepInvalidatesWorkAndWakeRequestsFreshSnapshot() = runTest {
        var published = 0
        val pipeline = AccessibilityEventPipeline(this, eventDispatcher = StandardTestDispatcher(testScheduler)) { current ->
            delay(10)
            if (current()) published++
        }
        pipeline.start()
        pipeline.requestRefresh()
        runCurrent()
        pipeline.setSuspended(true)
        repeat(10) { pipeline.requestRefresh() }
        advanceTimeBy(20)
        assertEquals(0, published)
        pipeline.setSuspended(false)
        advanceTimeBy(20)
        assertEquals(1, published)
        pipeline.stop()
    }

    @Test
    fun stoppedPipelineDropsPendingWorkAndCanRestart() = runTest {
        var published = 0
        val pipeline = AccessibilityEventPipeline(this, eventDispatcher = StandardTestDispatcher(testScheduler)) { current ->
            delay(10)
            if (current()) published++
        }
        pipeline.start()
        pipeline.requestRefresh()
        runCurrent()
        pipeline.requestRefresh()
        pipeline.stop()
        advanceTimeBy(20)
        assertEquals(0, published)
        pipeline.start()
        pipeline.requestRefresh()
        advanceTimeBy(20)
        assertEquals(1, published)
        pipeline.stop()
    }

    @Test
    fun workerSurvivesFailedWindowLookupAndIgnoresNonVisualEvents() = runTest {
        var calls = 0
        var failures = 0
        val pipeline = AccessibilityEventPipeline(this,
            eventDispatcher = StandardTestDispatcher(testScheduler),
            onError = { failures++ }) {
            calls++
            if (calls == 1) error("Window disappeared")
        }
        pipeline.start()
        pipeline.trySendEventType(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
        runCurrent()
        assertEquals(0, calls)
        pipeline.requestRefresh()
        runCurrent()
        pipeline.requestRefresh()
        runCurrent()
        assertEquals(2, calls)
        assertEquals(1, failures)
        pipeline.stop()
    }

    @Test
    fun processesEventImmediately() = runTest {
        var processCount = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = AccessibilityEventPipeline(
            scope = this,
            settledRefreshDelayMs = 10L,
            eventDispatcher = dispatcher,
            onProcess = { processCount++ }
        )

        pipeline.start()
        pipeline.trySendEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        runCurrent()

        assertEquals(1, processCount)
        pipeline.stop()
    }

    @Test
    fun schedulesSettledRefreshForContentChanged() = runTest {
        var processCount = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = AccessibilityEventPipeline(
            scope = this,
            settledRefreshDelayMs = 10L,
            eventDispatcher = dispatcher,
            onProcess = { processCount++ }
        )

        pipeline.start()
        pipeline.trySendEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        runCurrent()
        assertEquals(1, processCount)

        advanceTimeBy(10L)
        runCurrent()

        assertEquals(2, processCount)
        pipeline.stop()
    }

    @Test
    fun debouncesSettledRefreshes() = runTest {
        var processCount = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = AccessibilityEventPipeline(
            scope = this,
            settledRefreshDelayMs = 10L,
            eventDispatcher = dispatcher,
            onProcess = { processCount++ }
        )

        pipeline.start()
        pipeline.trySendEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        runCurrent()
        pipeline.trySendEventType(AccessibilityEvent.TYPE_VIEW_CLICKED)
        runCurrent()
        pipeline.trySendEventType(AccessibilityEvent.TYPE_VIEW_SELECTED)
        runCurrent()

        assertEquals(3, processCount)

        advanceTimeBy(9L)
        runCurrent()
        assertEquals(3, processCount)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(4, processCount)
        pipeline.stop()
    }

    @Test
    fun settledRefreshSurvivesConflationByNonRefreshEvent() = runTest {
        var processCount = 0
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pipeline = AccessibilityEventPipeline(
            scope = this,
            settledRefreshDelayMs = 10L,
            eventDispatcher = dispatcher,
            onProcess = { processCount++ }
        )

        pipeline.start()
        pipeline.trySendEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        pipeline.trySendEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        runCurrent()

        assertEquals(1, processCount)

        advanceTimeBy(10L)
        runCurrent()

        assertEquals(2, processCount)
        pipeline.stop()
    }

    @Test
    fun classifiesRefreshWorthyEvents() = runTest {
        val pipeline = AccessibilityEventPipeline(
            scope = this,
            onProcess = {}
        )

        assertTrue(pipeline.shouldScheduleSettledRefresh(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
        assertTrue(pipeline.shouldScheduleSettledRefresh(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
        assertTrue(pipeline.shouldScheduleSettledRefresh(AccessibilityEvent.TYPE_VIEW_CLICKED))
        assertTrue(pipeline.shouldScheduleSettledRefresh(AccessibilityEvent.TYPE_VIEW_SELECTED))
        assertTrue(pipeline.shouldScheduleSettledRefresh(AccessibilityEvent.TYPE_VIEW_SCROLLED))
        assertFalse(pipeline.shouldScheduleSettledRefresh(AccessibilityEvent.TYPE_VIEW_FOCUSED))
    }
}
