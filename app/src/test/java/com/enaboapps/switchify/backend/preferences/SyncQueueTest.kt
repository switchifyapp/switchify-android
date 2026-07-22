package com.enaboapps.switchify.backend.preferences

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncQueueTest {
    @Test
    fun queueChangeDoesNotRunUploaderOnCaller() = runTest {
        var uploadCount = 0
        val queue = createQueue {
            uploadCount++
            Result.success(Unit)
        }

        queue.queueChange("delay", 1000L)

        assertEquals(0, uploadCount)
        assertEquals(1, queue.getPendingCount())
    }

    @Test
    fun rapidChangesProduceOneBatchWithLatestValues() = runTest {
        val uploads = mutableListOf<Map<String, Any>>()
        val queue = createQueue { changes ->
            uploads += changes
            Result.success(Unit)
        }

        queue.queueChange("delay", 1000L)
        runCurrent()
        advanceTimeBy(2000L)
        queue.queueChange("delay", 1500L)
        queue.queueChange("speed", 2)
        runCurrent()
        advanceTimeBy(2999L)
        runCurrent()

        assertTrue(uploads.isEmpty())

        advanceTimeBy(1L)
        runCurrent()

        assertEquals(1, uploads.size)
        assertEquals(1500L, uploads.single()["delay"])
        assertEquals(2, uploads.single()["speed"])
        assertEquals(0, queue.getPendingCount())
    }

    @Test
    fun changeDuringUploadDoesNotCancelItAndSchedulesFollowUp() = runTest {
        val firstUploadStarted = CompletableDeferred<Unit>()
        val finishFirstUpload = CompletableDeferred<Unit>()
        val uploads = mutableListOf<Map<String, Any>>()
        var uploadCount = 0
        var firstUploadCancelled = false
        val queue = createQueue { changes ->
            uploads += changes
            uploadCount++
            if (uploadCount == 1) {
                firstUploadStarted.complete(Unit)
                try {
                    finishFirstUpload.await()
                } catch (error: CancellationException) {
                    firstUploadCancelled = true
                    throw error
                }
            }
            Result.success(Unit)
        }

        queue.queueChange("delay", 1000L)
        runCurrent()
        advanceTimeBy(3000L)
        runCurrent()
        firstUploadStarted.await()

        queue.queueChange("delay", 2000L)
        runCurrent()

        assertFalse(firstUploadCancelled)
        assertEquals(1, uploads.size)

        finishFirstUpload.complete(Unit)
        runCurrent()
        advanceTimeBy(3000L)
        runCurrent()

        assertEquals(
            listOf(mapOf("delay" to 1000L), mapOf("delay" to 2000L)),
            uploads
        )
    }

    @Test
    fun forceSyncBypassesDebounceAndInvalidatesDelayedWork() = runTest {
        val uploads = mutableListOf<Map<String, Any>>()
        val queue = createQueue { changes ->
            uploads += changes
            Result.success(Unit)
        }

        queue.queueChange("delay", 1000L)
        runCurrent()
        queue.forceSyncNow()
        runCurrent()

        assertEquals(listOf(mapOf("delay" to 1000L)), uploads)

        advanceTimeBy(3000L)
        runCurrent()

        assertEquals(1, uploads.size)
    }

    @Test
    fun forceSyncWaitsForActiveUploadWithoutOverlap() = runTest {
        val firstUploadStarted = CompletableDeferred<Unit>()
        val finishFirstUpload = CompletableDeferred<Unit>()
        val uploads = mutableListOf<Map<String, Any>>()
        var activeUploads = 0
        var maxActiveUploads = 0
        val queue = createQueue { changes ->
            activeUploads++
            maxActiveUploads = maxOf(maxActiveUploads, activeUploads)
            uploads += changes
            if (uploads.size == 1) {
                firstUploadStarted.complete(Unit)
                finishFirstUpload.await()
            }
            activeUploads--
            Result.success(Unit)
        }

        queue.queueChange("delay", 1000L)
        runCurrent()
        advanceTimeBy(3000L)
        runCurrent()
        firstUploadStarted.await()

        queue.queueChange("speed", 2)
        queue.forceSyncNow()
        runCurrent()

        assertEquals(1, uploads.size)
        assertEquals(1, maxActiveUploads)

        finishFirstUpload.complete(Unit)
        runCurrent()

        assertEquals(2, uploads.size)
        assertEquals(mapOf("speed" to 2), uploads.last())
        assertEquals(1, maxActiveUploads)
    }

    @Test
    fun pauseInvalidatesScheduledSync() = runTest {
        val uploads = mutableListOf<Map<String, Any>>()
        val queue = createQueue { changes ->
            uploads += changes
            Result.success(Unit)
        }

        queue.queueChange("delay", 1000L)
        runCurrent()
        queue.pause()
        queue.resume()
        advanceTimeBy(3000L)
        runCurrent()

        assertTrue(uploads.isEmpty())
        assertEquals(1, queue.getPendingCount())
    }

    @Test
    fun clearInvalidatesScheduledSyncAndRemovesPendingChanges() = runTest {
        val uploads = mutableListOf<Map<String, Any>>()
        val queue = createQueue { changes ->
            uploads += changes
            Result.success(Unit)
        }

        queue.queueChange("delay", 1000L)
        runCurrent()
        queue.clearQueue()
        advanceTimeBy(3000L)
        runCurrent()

        assertTrue(uploads.isEmpty())
        assertEquals(0, queue.getPendingCount())
    }

    @Test
    fun successfulUploadKeepsNewerValuePending() = runTest {
        val finishFirstUpload = CompletableDeferred<Unit>()
        val uploads = mutableListOf<Map<String, Any>>()
        val queue = createQueue { changes ->
            uploads += changes
            if (uploads.size == 1) {
                finishFirstUpload.await()
            }
            Result.success(Unit)
        }

        queue.queueChange("delay", 1000L)
        runCurrent()
        advanceTimeBy(3000L)
        runCurrent()
        queue.queueChange("delay", 2000L)
        finishFirstUpload.complete(Unit)
        runCurrent()

        assertEquals(1, queue.getPendingCount())

        advanceTimeBy(3000L)
        runCurrent()

        assertEquals(mapOf("delay" to 2000L), uploads.last())
        assertEquals(0, queue.getPendingCount())
    }

    @Test
    fun failedUploadRetainsChangesForForcedRetry() = runTest {
        val uploads = mutableListOf<Map<String, Any>>()
        val queue = createQueue { changes ->
            uploads += changes
            if (uploads.size == 1) {
                Result.failure(IllegalStateException("offline"))
            } else {
                Result.success(Unit)
            }
        }

        queue.queueChange("delay", 1000L)
        runCurrent()
        advanceTimeBy(3000L)
        runCurrent()

        assertEquals(1, queue.getPendingCount())

        queue.forceSyncNow()
        runCurrent()

        assertEquals(2, uploads.size)
        assertEquals(uploads[0], uploads[1])
        assertEquals(0, queue.getPendingCount())
    }

    private fun kotlinx.coroutines.test.TestScope.createQueue(
        uploader: suspend (Map<String, Any>) -> Result<Unit>
    ): SyncQueue {
        return SyncQueue(
            coroutineScope = backgroundScope,
            syncDelayMs = 3000L,
            uploader = uploader,
            logger = object : SyncQueueLogger {
                override fun debug(message: String) = Unit
                override fun info(message: String) = Unit
                override fun error(message: String, throwable: Throwable) = Unit
            }
        )
    }
}
