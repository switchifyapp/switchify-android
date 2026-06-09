package com.enaboapps.switchify.service.core

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
