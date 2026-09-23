package com.enaboapps.switchify.service.scanning

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanningSchedulerTest {
    @Test
    fun timingUpdatePreservesRunningStateAndUsesNewInterval() = runTest {
        var scanCount = 0
        val scheduler = scheduler { scanCount++ }
        scheduler.startScanning(initialDelay = 100, period = 100)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, scanCount)

        scheduler.updateTiming(initialDelay = 25, period = 25)
        advanceTimeBy(24)
        runCurrent()
        assertEquals(1, scanCount)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, scanCount)
        assertTrue(scheduler.isScanning())
        scheduler.shutdown()
    }

    @Test
    fun timingUpdateLeavesPausedScannerPaused() = runTest {
        var scanCount = 0
        val scheduler = scheduler { scanCount++ }
        scheduler.startScanning(initialDelay = 100, period = 100)
        scheduler.pauseScanning()

        scheduler.updateTiming(initialDelay = 25, period = 25)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(0, scanCount)
        assertTrue(scheduler.isPaused())
        scheduler.shutdown()
    }

    @Test
    fun timingUpdateLeavesStoppedScannerStopped() = runTest {
        var scanCount = 0
        val scheduler = scheduler { scanCount++ }

        scheduler.updateTiming(initialDelay = 25, period = 25)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(0, scanCount)
        assertTrue(scheduler.isStopped())
        scheduler.shutdown()
    }

    private fun TestScope.scheduler(onScan: suspend () -> Unit): ScanningScheduler {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ScanningScheduler(
            onScan = onScan,
            scanRateProvider = { 100L },
            firstItemPauseProvider = { 0L },
            coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
        )
    }
}
