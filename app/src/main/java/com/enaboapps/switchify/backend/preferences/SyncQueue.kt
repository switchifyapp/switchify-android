package com.enaboapps.switchify.backend.preferences

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val SYNC_QUEUE_TAG = "SyncQueue"

/**
 * Manages queuing and batching of preference sync operations.
 * Implements debouncing with a 3-second delay to avoid excessive network calls.
 */
class SyncQueue internal constructor(
    private val coroutineScope: CoroutineScope,
    private val syncDelayMs: Long,
    private val uploader: suspend (Map<String, Any>) -> Result<Unit>,
    private val logger: SyncQueueLogger = AndroidSyncQueueLogger
) {
    private val pendingChanges = ConcurrentHashMap<String, Any>()
    private val syncSignals = Channel<Long>(Channel.CONFLATED)
    private val syncGeneration = AtomicLong(0L)
    private val syncMutex = Mutex()

    @Volatile
    private var isPaused = false

    private constructor() : this(
        coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        syncDelayMs = SYNC_DELAY_MS,
        uploader = { changes -> PreferenceSync.getInstance().uploadBatchedChanges(changes) }
    )

    init {
        coroutineScope.launch {
            processSyncSignals()
        }
    }

    companion object {
        private const val SYNC_DELAY_MS = 3000L

        @Volatile
        private var instance: SyncQueue? = null

        fun getInstance(): SyncQueue {
            return instance ?: synchronized(this) {
                instance ?: SyncQueue().also { instance = it }
            }
        }
    }

    /** Queues a preference change for sync after the debounce delay. */
    fun queueChange(key: String, value: Any) {
        if (isPaused) {
            logger.debug("SyncQueue is paused, ignoring change for key: $key")
            return
        }

        logger.debug("Queueing change for key: $key")

        pendingChanges[key] = value
        val generation = syncGeneration.incrementAndGet()
        syncSignals.trySend(generation)
    }

    /**
     * Forces immediate sync of all pending changes without delay.
     */
    fun forceSyncNow() {
        logger.debug("Forcing immediate sync")

        syncGeneration.incrementAndGet()
        coroutineScope.launch {
            performBatchedSync()
        }
    }

    private suspend fun processSyncSignals() {
        for (initialGeneration in syncSignals) {
            var scheduledGeneration = initialGeneration
            while (true) {
                val newerGeneration = withTimeoutOrNull(syncDelayMs) {
                    syncSignals.receive()
                } ?: break
                scheduledGeneration = newerGeneration
            }

            performBatchedSync(scheduledGeneration)
        }
    }

    /**
     * Performs batched sync of all pending changes.
     */
    private suspend fun performBatchedSync(expectedGeneration: Long? = null) {
        syncMutex.withLock {
            if (
                expectedGeneration != null &&
                (isPaused || expectedGeneration != syncGeneration.get())
            ) {
                return
            }

            if (pendingChanges.isEmpty()) {
                logger.debug("No pending changes to sync")
                return
            }

            try {
                val changesToSync = pendingChanges.toMap()
                logger.info("Syncing ${changesToSync.size} batched changes")

                uploader(changesToSync).fold(
                    onSuccess = {
                        changesToSync.forEach { (key, value) ->
                            pendingChanges.remove(key, value)
                        }
                        logger.info("Batched sync completed successfully")
                    },
                    onFailure = { error ->
                        logger.error("Batched sync failed", error)
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.error("Error during batched sync", error)
            }
        }
    }

    /**
     * Clears all pending changes (useful for logout scenarios).
     */
    fun clearQueue() {
        logger.debug("Clearing sync queue")
        syncGeneration.incrementAndGet()
        pendingChanges.clear()
    }

    /**
     * Pauses the sync queue to prevent conflicts during authentication sync.
     */
    fun pause() {
        logger.debug("Pausing sync queue")
        isPaused = true
        syncGeneration.incrementAndGet()
    }

    /**
     * Resumes the sync queue after authentication sync is complete.
     */
    fun resume() {
        logger.debug("Resuming sync queue")
        isPaused = false
    }

    /**
     * Returns the number of pending changes.
     */
    fun getPendingCount(): Int = pendingChanges.size
}

internal interface SyncQueueLogger {
    fun debug(message: String)
    fun info(message: String)
    fun error(message: String, throwable: Throwable)
}

private object AndroidSyncQueueLogger : SyncQueueLogger {
    override fun debug(message: String) {
        Log.d(SYNC_QUEUE_TAG, message)
    }

    override fun info(message: String) {
        Log.i(SYNC_QUEUE_TAG, message)
    }

    override fun error(message: String, throwable: Throwable) {
        Log.e(SYNC_QUEUE_TAG, message, throwable)
    }
}
