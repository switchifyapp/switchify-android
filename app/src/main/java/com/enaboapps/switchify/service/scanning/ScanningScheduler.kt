package com.enaboapps.switchify.service.scanning

import android.content.Context
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ScanningScheduler is a class that manages the scheduling of scanning tasks.
 * It uses coroutines to run the scanning tasks asynchronously and provides methods
 * to start, stop, pause, and resume scanning operations.
 *
 * @property context The application context.
 * @property onScan A suspend function that gets executed during each scan.
 */
class ScanningScheduler internal constructor(
    private val onScan: suspend () -> Unit,
    private val scanRateProvider: () -> Long,
    private val firstItemPauseProvider: () -> Long,
    private val coroutineScope: CoroutineScope,
    private val intervalOwner: String = UUID.randomUUID().toString(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val onInterval: (ScanIntervalEvent) -> Unit = {}
) {

    constructor(context: Context, onScan: suspend () -> Unit) : this(
        context, UUID.randomUUID().toString(), {}, onScan
    )

    constructor(
        context: Context,
        intervalOwner: String = UUID.randomUUID().toString(),
        onInterval: (ScanIntervalEvent) -> Unit = {},
        onScan: suspend () -> Unit
    ) : this(
        onScan = { withContext(Dispatchers.Main.immediate) { onScan() } },
        scanRateProvider = ScanSettings(context)::getScanRate,
        firstItemPauseProvider = ScanSettings(context)::getPauseOnFirstItemDelay,
        coroutineScope = CoroutineScope(Dispatchers.IO + CoroutineName(UUID.randomUUID().toString())),
        intervalOwner = intervalOwner,
        clock = SystemClock::uptimeMillis,
        onInterval = onInterval
    )

    /**
     * The unique identifier of the scanner.
     */
    private val uniqueId = UUID.randomUUID().toString()

    /**
     * The CoroutineScope in which the scanning tasks are launched.
     */
    /**
     * The Job representing the currently running scanning task.
     */
    private var scanningJob: Job? = null
    private val intervalGeneration = AtomicLong()

    /**
     * A flag indicating whether a scanning task is currently executing.
     */
    private val isExecuting = AtomicBoolean(false)

    /**
     * The initial delay before the first scanning task is launched.
     */
    private var initialDelay: Long = 0L

    /**
     * The period between successive scanning tasks.
     */
    private var period: Long = 1000L // Default period of 1 second

    /**
     * The current state of the scanner.
     */
    private var scanState = AtomicReference(ScanState.STOPPED)

    /**
     * Starts the scanning tasks.
     *
     * @param initialDelay The initial delay before the first scanning task is launched.
     * @param period The period between successive scanning tasks.
     */
    fun startScanning(
        initialDelay: Long = scanRateProvider(),
        period: Long = scanRateProvider()
    ) {
        if (scanState.get() == ScanState.SCANNING) {
            println("[$uniqueId] Already scanning")
            return
        }

        scanState.set(ScanState.SCANNING)

        val initialDelayPlusPause = initialDelay + firstItemPauseProvider()

        this.initialDelay = initialDelay
        this.period = period

        launchScanningJob(initialDelayPlusPause)
    }

    internal fun updateTiming(initialDelay: Long, period: Long) {
        this.initialDelay = initialDelay
        this.period = period
        if (scanState.get() == ScanState.SCANNING) {
            launchScanningJob(initialDelay)
        }
    }

    internal fun restartCurrentInterval() {
        if (isScanning()) launchScanningJob(period)
    }

    internal fun resumeAfterEmptySnapshot() {
        if (!isScanning()) {
            scanState.set(ScanState.SCANNING)
            launchScanningJob(period)
        }
    }

    internal fun restorePausedState() {
        scanState.compareAndSet(ScanState.STOPPED, ScanState.PAUSED)
    }

    private fun launchScanningJob(delayMillis: Long) {
        val generation = intervalGeneration.incrementAndGet()
        scanningJob?.cancel()
        scanningJob = coroutineScope.launch {
            try {
                println("[$uniqueId] Starting scanning job")
                publishInterval(generation, delayMillis)
                delay(delayMillis)
                while (isActive) {
                    if (isExecuting.compareAndSet(false, true)) {
                        try {
                            onScan()
                        } catch (cancelled: CancellationException) {
                            // Only our own cancellation ends the loop. A foreign
                            // cancellation (e.g. a timeout inside a step) is an
                            // ordinary step failure; ending the loop here would
                            // leave scanState at SCANNING with no job behind it.
                            if (!isActive) throw cancelled
                            println("[$uniqueId] Scan step cancelled: ${cancelled.message}")
                        } catch (e: Exception) {
                            println("[$uniqueId] Error during scan: ${e.message}")
                            e.printStackTrace()
                        } finally {
                            isExecuting.set(false)
                        }
                    }
                    if (isActive && generation == intervalGeneration.get()) {
                        publishInterval(generation, period)
                    }
                    delay(period)
                }
            } finally {
                if (intervalGeneration.compareAndSet(generation, generation + 1)) {
                    onInterval(ScanIntervalEvent(intervalOwner, generation + 1, null))
                }
            }
        }
    }

    private fun publishInterval(generation: Long, durationMillis: Long) {
        if (generation == intervalGeneration.get()) {
            onInterval(ScanIntervalEvent(intervalOwner, generation,
                ScanInterval(clock(), durationMillis.coerceAtLeast(0L))))
        }
    }

    private fun clearInterval() {
        onInterval(ScanIntervalEvent(intervalOwner, intervalGeneration.incrementAndGet(), null))
    }

    /**
     * Checks if the scanner is currently scanning.
     *
     * @return True if the scanner is scanning, false otherwise.
     */
    fun isScanning(): Boolean = scanState.get() == ScanState.SCANNING

    /**
     * Checks if the scanner is currently paused.
     *
     * @return True if the scanner is paused, false otherwise.
     */
    fun isPaused(): Boolean = scanState.get() == ScanState.PAUSED

    /**
     * Checks if the scanner is currently stopped.
     *
     * @return True if the scanner is stopped, false otherwise.
     */
    fun isStopped(): Boolean = scanState.get() == ScanState.STOPPED

    /**
     * Stops the scanning tasks.
     */
    fun stopScanning() {
        println("[$uniqueId] Attempting to stop scanning... $scanState")
        try {
            if (scanState.get() == ScanState.SCANNING || scanState.get() == ScanState.PAUSED) {
                scanState.set(ScanState.STOPPED)
                clearInterval()
                scanningJob?.cancel()
            }
        } catch (e: Exception) {
            println("[$uniqueId] Error while stopping scanning: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Pauses the scanning tasks.
     */
    fun pauseScanning() {
        println("[$uniqueId] Attempting to pause scanning... $scanState")
        try {
            if (scanState.compareAndSet(ScanState.SCANNING, ScanState.PAUSED)) {
                clearInterval()
                scanningJob?.cancel()
            }
        } catch (e: Exception) {
            println("[$uniqueId] Error while pausing scanning: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Resumes the scanning tasks.
     */
    fun resumeScanning() {
        println("[$uniqueId] Attempting to resume scanning... $scanState")
        try {
            if (scanState.get() == ScanState.PAUSED) {
                startScanning(initialDelay, period)
            }
        } catch (e: Exception) {
            println("[$uniqueId] Error while resuming scanning: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Shuts down the CoroutineScope, cancelling all active coroutines.
     */
    fun shutdown() {
        println("[$uniqueId] Shutting down scope")
        try {
            scanState.set(ScanState.STOPPED)
            clearInterval()
            coroutineScope.cancel() // Cancel all coroutines started by this scope
        } catch (e: Exception) {
            println("[$uniqueId] Error while shutting down: ${e.message}")
            e.printStackTrace()
        }
    }
}
