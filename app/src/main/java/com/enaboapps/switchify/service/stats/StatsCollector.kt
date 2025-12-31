package com.enaboapps.switchify.service.stats

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.stats.database.StatsEntity
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton collector for stats events.
 * Batches events in memory and writes them to database periodically for performance.
 */
class StatsCollector private constructor() {
    private val eventQueue = ConcurrentLinkedQueue<PendingEvent>()
    private val batchJob = AtomicReference<Job?>(null)
    private var repository: StatsRepository? = null
    private var context: Context? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "StatsCollector"
        private const val BATCH_DELAY_MS = 5000L  // 5 second batching
        private const val MAX_QUEUE_SIZE = 1000

        @Volatile
        private var instance: StatsCollector? = null

        fun getInstance(): StatsCollector {
            return instance ?: synchronized(this) {
                instance ?: StatsCollector().also { instance = it }
            }
        }
    }

    /**
     * Initializes the collector with a context.
     * Must be called before recording events.
     * Only initializes if device is unlocked to prevent crashes.
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext

        // Do NOT initialize repository when device is locked - database access will crash
        if (!DeviceLockObserver.isUserUnlocked(context)) {
            Log.w(TAG, "Device is locked, deferring StatsCollector initialization")
            return
        }

        repository = StatsRepository(context)
        Log.d(TAG, "StatsCollector initialized")
    }

    /**
     * Attempts to initialize if not already initialized.
     * Call this when device unlocks.
     */
    fun ensureInitialized() {
        if (repository != null) return

        val ctx = context ?: return
        if (!DeviceLockObserver.isUserUnlocked(ctx)) {
            Log.d(TAG, "Cannot initialize, device still locked")
            return
        }

        repository = StatsRepository(ctx)
        Log.d(TAG, "StatsCollector initialized after device unlock")
    }

    /**
     * Records a switch press event.
     * Non-blocking - queues the event for batched write.
     */
    fun recordSwitchPress(switchType: String, switchCode: String) {
        if (repository == null) {
            Log.w(TAG, "StatsCollector not initialized, cannot record switch press")
            return
        }
        queueEvent(PendingEvent.SwitchPress(switchType, switchCode, System.currentTimeMillis()))
    }

    /**
     * Records a menu open event.
     * Non-blocking - queues the event for batched write.
     */
    fun recordMenuOpen(menuId: String, fromMenuId: String? = null) {
        if (repository == null) {
            Log.w(TAG, "StatsCollector not initialized, cannot record menu open")
            return
        }
        queueEvent(PendingEvent.MenuOpen(menuId, fromMenuId, System.currentTimeMillis()))
    }

    /**
     * Queues an event for batched write.
     */
    private fun queueEvent(event: PendingEvent) {
        // Prevent unbounded growth
        if (eventQueue.size >= MAX_QUEUE_SIZE) {
            eventQueue.poll()  // Drop oldest
            Log.w(TAG, "Event queue full, dropping oldest event")
        }

        eventQueue.offer(event)
        scheduleBatchWrite()
    }

    /**
     * Schedules a batched write of queued events.
     * Uses debouncing to avoid excessive database writes.
     */
    private fun scheduleBatchWrite() {
        // Cancel previous batch job
        batchJob.get()?.cancel()

        // Schedule new batch write
        val job = coroutineScope.launch {
            delay(BATCH_DELAY_MS)
            flushEvents()
        }
        batchJob.set(job)
    }

    /**
     * Flushes all queued events to the database.
     * Only flushes if device is unlocked to prevent crashes.
     */
    private suspend fun flushEvents() {
        // Check if device is unlocked before attempting database operations
        val ctx = context
        if (ctx == null) {
            Log.w(TAG, "Context is null, cannot flush events")
            return
        }

        if (!DeviceLockObserver.isUserUnlocked(ctx)) {
            Log.d(TAG, "Device is locked, postponing flush (${eventQueue.size} events queued)")
            // Don't reschedule - events will be flushed when device unlocks
            return
        }

        // Try to initialize if not already done
        ensureInitialized()

        val events = mutableListOf<PendingEvent>()
        while (eventQueue.isNotEmpty()) {
            eventQueue.poll()?.let { events.add(it) }
        }

        if (events.isNotEmpty()) {
            try {
                val statsEntities = events.map { it.toStatsEntity() }
                if (repository == null) {
                    Log.e(TAG, "Repository is null, cannot flush ${events.size} events")
                    // Put events back in queue since we couldn't flush
                    events.forEach { eventQueue.offer(it) }
                    return
                }
                repository?.batchInsertEvents(statsEntities)
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing events", e)
                // Put events back in queue on error
                events.forEach { eventQueue.offer(it) }
            }
        }
    }

    /**
     * Forces an immediate flush of all queued events.
     * Useful for testing or when app is closing.
     */
    suspend fun forceFlush() {
        batchJob.get()?.cancel()
        flushEvents()
    }

    /**
     * Sealed class representing pending events to be written.
     */
    sealed class PendingEvent {
        abstract fun toStatsEntity(): StatsEntity

        data class SwitchPress(
            val type: String,
            val code: String,
            val timestamp: Long
        ) : PendingEvent() {
            override fun toStatsEntity() = StatsEntity(
                eventType = "switch_press",
                eventSubtype = "${type}_${code}",
                timestamp = timestamp
            )
        }

        data class MenuOpen(
            val menuId: String,
            val fromMenuId: String?,
            val timestamp: Long
        ) : PendingEvent() {
            override fun toStatsEntity() = StatsEntity(
                eventType = "menu_open",
                eventSubtype = menuId,
                timestamp = timestamp
            )
        }
    }
}
