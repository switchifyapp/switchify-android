package com.enaboapps.switchify.service.stats

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.stats.database.StatsEntity
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton collector for stats events.
 * Batches events in memory and writes them to database periodically for performance.
 * Implements AutoCloseable to properly clean up resources.
 */
class StatsCollector private constructor() : AutoCloseable {
    private val eventQueue = ConcurrentLinkedQueue<QueuedEvent>()
    private val queueSize = AtomicInteger(0)
    private val batchJob = AtomicReference<Job?>(null)
    private var repository: StatsRepository? = null
    private var context: Context? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isClosed = AtomicBoolean(false)

    companion object {
        private const val TAG = "StatsCollector"
        private const val BATCH_DELAY_MS = 5000L  // 5 second batching
        private const val MAX_QUEUE_SIZE = 1000
        private const val MAX_RETRY_COUNT = 3  // Drop events after 3 failed attempts

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
        Log.i(TAG, "StatsCollector.initialize() called")
        this.context = context.applicationContext

        // Do NOT initialize repository when device is locked - database access will crash
        if (!DeviceLockObserver.isUserUnlocked(context)) {
            Log.w(TAG, "Device is locked, deferring StatsCollector initialization")
            return
        }

        repository = StatsRepository(context)
        Log.i(TAG, "StatsCollector initialized successfully with repository")
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
        if (isClosed.get()) {
            Log.w(TAG, "StatsCollector is closed, cannot record switch press")
            return
        }
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
    fun recordMenuOpen(menuId: String) {
        if (isClosed.get()) {
            Log.w(TAG, "StatsCollector is closed, cannot record menu open")
            return
        }
        if (repository == null) {
            Log.w(TAG, "StatsCollector not initialized, cannot record menu open")
            return
        }
        queueEvent(PendingEvent.MenuOpen(menuId, System.currentTimeMillis()))
    }

    /**
     * Queues an event for batched write.
     */
    private fun queueEvent(event: PendingEvent) {
        // Prevent unbounded growth
        while (queueSize.get() >= MAX_QUEUE_SIZE) {
            if (eventQueue.poll() != null) {
                queueSize.decrementAndGet()
                Log.w(TAG, "Event queue full, dropping oldest event")
            } else {
                break
            }
        }

        if (eventQueue.offer(QueuedEvent(event, retryCount = 0))) {
            queueSize.incrementAndGet()
        }
        scheduleBatchWrite()
    }

    /**
     * Schedules a batched write of queued events.
     * Uses debouncing to avoid excessive database writes.
     */
    private fun scheduleBatchWrite() {
        // Schedule new batch write
        val newJob = coroutineScope.launch {
            delay(BATCH_DELAY_MS)
            flushEvents()
        }

        // Atomically replace the old job and cancel it
        val oldJob = batchJob.getAndSet(newJob)
        oldJob?.cancel()
    }

    /**
     * Flushes all queued events to the database.
     * Only flushes if device is unlocked to prevent crashes.
     */
    private suspend fun flushEvents() {
        if (isClosed.get()) {
            Log.w(TAG, "StatsCollector is closed, skipping flush")
            return
        }

        val ctx = context
        if (ctx == null) {
            Log.w(TAG, "Context is null, cannot flush events")
            return
        }

        if (!DeviceLockObserver.isUserUnlocked(ctx)) {
            Log.d(TAG, "Device is locked, postponing flush (${queueSize.get()} events queued)")
            return
        }

        ensureInitialized()

        val queuedEvents = mutableListOf<QueuedEvent>()
        while (eventQueue.isNotEmpty()) {
            eventQueue.poll()?.let {
                queuedEvents.add(it)
                queueSize.decrementAndGet()
            }
        }

        if (queuedEvents.isNotEmpty()) {
            try {
                val statsEntities = queuedEvents.map { it.event.toStatsEntity() }
                if (repository == null) {
                    Log.e(TAG, "Repository is null, cannot flush ${queuedEvents.size} events")
                    Logger.log(
                        LogEvent.StatsFlushFailed,
                        data = mapOf(
                            "result" to "failure",
                            "reason" to "repository_null",
                            "queued_count" to queuedEvents.size
                        )
                    )
                    requeueEventsWithRetry(queuedEvents)
                    return
                }
                repository?.batchInsertEvents(statsEntities)
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing events", e)
                Logger.log(
                    LogEvent.StatsFlushFailed,
                    data = mapOf(
                        "result" to "failure",
                        "reason" to "exception",
                        "queued_count" to queuedEvents.size
                    ),
                    throwable = e
                )
                requeueEventsWithRetry(queuedEvents)
            }
        }
    }

    /**
     * Re-queues events after a failed flush attempt.
     * Increments retry count and drops events that exceed MAX_RETRY_COUNT.
     */
    private fun requeueEventsWithRetry(events: List<QueuedEvent>) {
        var droppedCount = 0
        var requeuedCount = 0

        events.forEach { queuedEvent ->
            val newRetryCount = queuedEvent.retryCount + 1
            if (newRetryCount > MAX_RETRY_COUNT) {
                droppedCount++
                Log.w(TAG, "Dropping event after $MAX_RETRY_COUNT failed attempts: ${queuedEvent.event}")
            } else {
                if (eventQueue.offer(QueuedEvent(queuedEvent.event, newRetryCount))) {
                    queueSize.incrementAndGet()
                    requeuedCount++
                } else {
                    droppedCount++
                    Log.w(TAG, "Failed to re-queue event, queue full")
                }
            }
        }

        if (droppedCount > 0) {
            Log.w(TAG, "Dropped $droppedCount events (max retries exceeded or queue full)")
        }
        if (requeuedCount > 0) {
            Log.i(TAG, "Re-queued $requeuedCount events for retry")
        }
    }

    /**
     * Forces an immediate flush of all queued events.
     * Useful for testing or when app is closing.
     */
    suspend fun forceFlush() {
        batchJob.getAndSet(null)?.cancel()
        flushEvents()
    }

    /**
     * Closes the collector and releases all resources.
     * Cancels the coroutine scope and any pending batch jobs.
     * Guards against double-close.
     */
    override fun close() {
        // Guard against double-close
        if (!isClosed.compareAndSet(false, true)) {
            Log.w(TAG, "StatsCollector already closed, ignoring duplicate close()")
            return
        }

        Log.i(TAG, "Closing StatsCollector and releasing resources")

        // Cancel any pending batch job
        batchJob.getAndSet(null)?.cancel()

        // Cancel the coroutine scope and all running coroutines
        coroutineScope.cancel()

        // Clear references
        repository = null
        context = null

        Log.i(TAG, "StatsCollector closed successfully")
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
            override fun toStatsEntity(): StatsEntity {
                val eventDate = java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .toString()
                return StatsEntity(
                    eventType = "switch_press",
                    eventSubtype = "${type}_${code}",
                    timestamp = timestamp,
                    eventDate = eventDate
                )
            }
        }

        data class MenuOpen(
            val menuId: String,
            val timestamp: Long
        ) : PendingEvent() {
            override fun toStatsEntity(): StatsEntity {
                val eventDate = java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .toString()
                return StatsEntity(
                    eventType = "menu_open",
                    eventSubtype = menuId,
                    timestamp = timestamp,
                    eventDate = eventDate
                )
            }
        }
    }

    /**
     * Wrapper for queued events that tracks retry attempts.
     * Prevents infinite retry loops on persistent failures.
     */
    private data class QueuedEvent(
        val event: PendingEvent,
        val retryCount: Int
    )
}
