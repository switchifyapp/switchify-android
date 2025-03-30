package com.enaboapps.switchify.service.scanning

import android.content.Context
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * ScanningScheduler is a class that manages the scheduling of scanning tasks.
 * It uses coroutines to run the scanning tasks asynchronously and provides methods
 * to start, stop, pause, and resume scanning operations.
 *
 * @property context The application context.
 * @property onScan A suspend function that gets executed during each scan.
 */
class ScanningScheduler(
    private val context: Context,
    private val onScan: suspend () -> Unit
) {

    /**
     * The unique identifier of the scanner.
     */
    private val uniqueId = UUID.randomUUID().toString()

    /**
     * The CoroutineScope in which the scanning tasks are launched.
     */
    private val coroutineScope = CoroutineScope(Dispatchers.IO + CoroutineName(uniqueId))

    /**
     * The Job representing the currently running scanning task.
     */
    private var scanningJob: Job? = null

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
     * The settings for the scanning tasks.
     */
    private val scanSettings = ScanSettings(context)

    /**
     * Starts the scanning tasks.
     *
     * @param initialDelay The initial delay before the first scanning task is launched.
     * @param period The period between successive scanning tasks.
     */
    fun startScanning(
        initialDelay: Long = scanSettings.getScanRate(),
        period: Long = scanSettings.getScanRate()
    ) {
        if (scanState.get() == ScanState.SCANNING) {
            println("[$uniqueId] Already scanning")
            return
        }

        scanState.set(ScanState.SCANNING)

        val initialDelayPlusPause = initialDelay + scanSettings.getPauseOnFirstItemDelay()

        this.initialDelay = initialDelay
        this.period = period

        scanningJob?.cancel()

        scanningJob = coroutineScope.launch {
            println("[$uniqueId] Starting scanning job")
            delay(initialDelayPlusPause)
            while (isActive) {
                if (isExecuting.compareAndSet(false, true)) {
                    try {
                        onScan()
                    } catch (e: Exception) {
                        println("[$uniqueId] Error during scan: ${e.message}")
                        e.printStackTrace()
                    } finally {
                        isExecuting.set(false)
                    }
                }
                delay(period)
            }
        }
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
            coroutineScope.cancel() // Cancel all coroutines started by this scope
        } catch (e: Exception) {
            println("[$uniqueId] Error while shutting down: ${e.message}")
            e.printStackTrace()
        }
    }
}