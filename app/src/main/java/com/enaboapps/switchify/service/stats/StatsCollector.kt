package com.enaboapps.switchify.service.stats

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.stats.database.StatsEntity
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
     */
    fun initialize(context: Context) {
        repository = StatsRepository(context)
        Log.d(TAG, "StatsCollector initialized")
    }

    /**
     * Records a switch press event.
     * Non-blocking - queues the event for batched write.
     */
    fun recordSwitchPress(switchType: String, switchCode: String) {
        queueEvent(PendingEvent.SwitchPress(switchType, switchCode, System.currentTimeMillis()))
    }

    /**
     * Records a menu open event.
     * Non-blocking - queues the event for batched write.
     */
    fun recordMenuOpen(menuId: String, fromMenuId: String? = null) {
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
     */
    private suspend fun flushEvents() {
        val events = mutableListOf<PendingEvent>()
        while (eventQueue.isNotEmpty()) {
            eventQueue.poll()?.let { events.add(it) }
        }

        if (events.isNotEmpty()) {
            try {
                val statsEntities = events.map { it.toStatsEntity() }
                repository?.batchInsertEvents(statsEntities)
                Log.d(TAG, "Flushed ${events.size} events to database")
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing events", e)
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
