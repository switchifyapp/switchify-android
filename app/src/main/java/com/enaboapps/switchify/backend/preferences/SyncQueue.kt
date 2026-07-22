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
    private data class PendingChange(
        val value: Any,
        val epoch: Long,
        val revision: Long
    )

    private data class SyncSignal(
        val generation: Long,
        val epoch: Long
    )

    private val queueLock = Any()
    private val pendingChanges = mutableMapOf<String, PendingChange>()
    private val syncSignals = Channel<SyncSignal>(Channel.CONFLATED)
    private val syncGeneration = AtomicLong(0L)
    private val syncMutex = Mutex()
    private var queueEpoch = 0L
    private var nextRevision = 0L

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
        val wasQueued = synchronized(queueLock) {
            if (isPaused) {
                false
            } else {
                logger.debug("Queueing change for key: $key")
                val revision = ++nextRevision
                pendingChanges[key] = PendingChange(value, queueEpoch, revision)
                val signal = SyncSignal(syncGeneration.incrementAndGet(), queueEpoch)
                syncSignals.trySend(signal)
                true
            }
        }
        if (!wasQueued) {
            logger.debug("SyncQueue is paused, ignoring change for key: $key")
        }
    }

    /**
     * Forces immediate sync of all pending changes without delay.
     */
    fun forceSyncNow() {
        logger.debug("Forcing immediate sync")

        val expectedEpoch = synchronized(queueLock) {
            syncGeneration.incrementAndGet()
            queueEpoch
        }
        coroutineScope.launch {
            performBatchedSync(expectedEpoch = expectedEpoch)
        }
    }

    private suspend fun processSyncSignals() {
        for (initialSignal in syncSignals) {
            var scheduledSignal = initialSignal
            while (true) {
                val newerSignal = withTimeoutOrNull(syncDelayMs) {
                    syncSignals.receive()
                } ?: break
                scheduledSignal = newerSignal
            }

            performBatchedSync(
                expectedGeneration = scheduledSignal.generation,
                expectedEpoch = scheduledSignal.epoch
            )
        }
    }

    /**
     * Performs batched sync of all pending changes.
     */
    private suspend fun performBatchedSync(
        expectedGeneration: Long? = null,
        expectedEpoch: Long
    ) {
        syncMutex.withLock {
            val changesToSync = synchronized(queueLock) {
                if (
                    expectedEpoch != queueEpoch ||
                    expectedGeneration != null &&
                    (isPaused || expectedGeneration != syncGeneration.get())
                ) {
                    return
                }
                pendingChanges.toMap()
            }

            if (changesToSync.isEmpty()) {
                logger.debug("No pending changes to sync")
                return
            }

            try {
                logger.info("Syncing ${changesToSync.size} batched changes")

                uploader(changesToSync.mapValues { it.value.value }).fold(
                    onSuccess = {
                        synchronized(queueLock) {
                            changesToSync.forEach { (key, change) ->
                                if (pendingChanges[key] == change) {
                                    pendingChanges.remove(key)
                                }
                            }
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
        synchronized(queueLock) {
            syncGeneration.incrementAndGet()
            queueEpoch++
            pendingChanges.clear()
        }
    }

    /**
     * Pauses the sync queue to prevent conflicts during authentication sync.
     */
    fun pause() {
        logger.debug("Pausing sync queue")
        synchronized(queueLock) {
            isPaused = true
            syncGeneration.incrementAndGet()
        }
    }

    /**
     * Resumes the sync queue after authentication sync is complete.
     */
    fun resume() {
        logger.debug("Resuming sync queue")
        synchronized(queueLock) {
            isPaused = false
        }
    }

    /**
     * Returns the number of pending changes.
     */
    fun getPendingCount(): Int = synchronized(queueLock) { pendingChanges.size }
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
