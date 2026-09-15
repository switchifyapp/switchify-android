package com.enaboapps.switchify.service.scanning

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanIntervalTest {
    @Test fun remainingTimeUsesClockAndClampsAtBothEnds() {
        val interval = ScanInterval(100, 200)
        assertEquals(1f, interval.remainingFraction(50), 0f)
        assertEquals(0.5f, interval.remainingFraction(200), 0f)
        assertEquals(0f, interval.remainingFraction(400), 0f)
        assertEquals(0f, ScanInterval(0, 0).remainingFraction(0), 0f)
    }

    @Test fun firstIntervalIncludesPauseAndLaterIntervalsStartAfterScanCompletes() = runTest {
        val events = mutableListOf<ScanIntervalEvent>()
        var scans = 0
        val scheduler = scheduler(events) { scans++; delay(20) }
        scheduler.startScanning(100, 80)
        runCurrent()
        assertEquals(ScanInterval(0, 150), events.last().interval)
        advanceTimeBy(149)
        runCurrent()
        assertEquals(0, scans)
        advanceTimeBy(21)
        runCurrent()
        assertEquals(1, scans)
        assertEquals(ScanInterval(170, 80), events.last().interval)
        scheduler.shutdown()
    }

    @Test fun pauseClearsCountdownAndResumeMatchesExistingFirstPauseSemantics() = runTest {
        val events = mutableListOf<ScanIntervalEvent>()
        val scheduler = scheduler(events) {}
        scheduler.startScanning(100, 80)
        runCurrent()
        advanceTimeBy(30)
        scheduler.pauseScanning()
        val cleared = events.last()
        assertNull(cleared.interval)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(cleared, events.last())
        scheduler.resumeScanning()
        runCurrent()
        assertEquals(ScanInterval(330, 150), events.last().interval)
        assertTrue(events.last().generation > cleared.generation)
        scheduler.shutdown()
    }

    @Test fun timingUpdateReplacesPendingDelayWithoutAnExtraPause() = runTest {
        val events = mutableListOf<ScanIntervalEvent>()
        var scans = 0
        val scheduler = scheduler(events) { scans++ }
        scheduler.startScanning(100, 100)
        runCurrent()
        val first = events.last()
        advanceTimeBy(20)
        scheduler.updateTiming(25, 25)
        runCurrent()
        assertEquals(ScanInterval(20, 25), events.last().interval)
        assertTrue(events.last().generation > first.generation)
        advanceTimeBy(25)
        runCurrent()
        assertEquals(1, scans)
        scheduler.shutdown()
        val stopped = events.last()
        assertNull(stopped.interval)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(stopped, events.last())
        assertEquals(1, scans)
    }

    @Test fun stoppingInsideScanDoesNotPublishAnotherCountdown() = runTest {
        val events = mutableListOf<ScanIntervalEvent>()
        lateinit var scheduler: ScanningScheduler
        scheduler = scheduler(events) { scheduler.stopScanning() }
        scheduler.startScanning(0, 20)
        advanceTimeBy(50)
        runCurrent()
        assertTrue(scheduler.isStopped())
        assertNull(events.last().interval)
        val count = events.size
        advanceTimeBy(100)
        runCurrent()
        assertEquals(count, events.size)
        scheduler.shutdown()
    }

    @Test fun scopeCancellationClearsThePublishedInterval() = runTest {
        val events = mutableListOf<ScanIntervalEvent>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = ScanningScheduler({}, { 100 }, { 0 }, scope,
            onInterval = events::add)
        scheduler.startScanning()
        runCurrent()
        assertNotNull(events.last().interval)
        scope.cancel()
        runCurrent()
        assertNull(events.last().interval)
    }

    @Test fun scannerReplacementAndOldGenerationsCannotStealCountdown() {
        val store = ScanIntervalStore()
        val app = ScanInterval(0, 100)
        val menu = ScanInterval(10, 200)
        store.record(ScanIntervalEvent("app", 3, app), 1)
        store.record(ScanIntervalEvent("menu", 1, menu), 2)
        store.record(ScanIntervalEvent("app", 2, ScanInterval(0, 900)), 3)
        assertEquals(app, store.intervalFor("app", 0))
        assertEquals(menu, store.intervalFor("menu", 0))
        assertNull(store.intervalFor("keyboard", 0))
        store.record(ScanIntervalEvent("menu", 2, null), 4)
        store.record(ScanIntervalEvent("menu", 1, menu), 5)
        assertNull(store.intervalFor("menu", 0))
    }

    @Test fun nextHighlightWaitsForItsOwnIntervalAndRetainsEventsBeforeAttachment() {
        val store = ScanIntervalStore()
        store.record(ScanIntervalEvent("menu", 1, ScanInterval(0, 100)), 1)
        assertNull(store.intervalFor("menu", 1))
        val next = ScanInterval(100, 100)
        store.record(ScanIntervalEvent("menu", 1, next), 2)
        assertEquals(next, store.intervalFor("menu", 1))
        store.clear()
        assertNull(store.intervalFor("menu", 0))
    }

    private fun TestScope.scheduler(events: MutableList<ScanIntervalEvent>, scan: suspend () -> Unit) =
        ScanningScheduler(scan, { 100L }, { 50L },
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            intervalOwner = "scanner", clock = { testScheduler.currentTime }, onInterval = events::add)

    @Test fun replacementGetsOneFullIntervalWithoutFirstItemPause() = runTest {
        val events = mutableListOf<ScanIntervalEvent>()
        var scans = 0
        val scheduler = scheduler(events) { scans++ }
        scheduler.startScanning(100, 100)
        runCurrent()
        advanceTimeBy(40)
        scheduler.restartCurrentInterval()
        runCurrent()
        assertEquals(ScanInterval(40, 100), events.last().interval)
        advanceTimeBy(99)
        runCurrent()
        assertEquals(0, scans)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, scans)
        scheduler.shutdown()
    }

    @Test fun emptySnapshotResumeUsesNormalIntervalAndPausedReplacementDoesNotStart() = runTest {
        val events = mutableListOf<ScanIntervalEvent>()
        val scheduler = scheduler(events) {}
        scheduler.startScanning(100, 100)
        runCurrent()
        scheduler.pauseScanning()
        scheduler.restartCurrentInterval()
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(scheduler.isPaused())
        assertNull(events.last().interval)
        scheduler.resumeAfterEmptySnapshot()
        runCurrent()
        assertEquals(ScanInterval(1000, 100), events.last().interval)
        scheduler.shutdown()
    }
}
