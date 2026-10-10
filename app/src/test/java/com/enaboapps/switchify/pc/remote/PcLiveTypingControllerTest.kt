package com.enaboapps.switchify.pc.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingStream(
    var chunkResult: suspend (String) -> Boolean = { true },
    var keyResult: suspend (String) -> Boolean = { true }
) : PcLiveTypingStream {
    val calls = mutableListOf<String>()

    override suspend fun streamChunk(text: String): Boolean {
        calls += "chunk:$text"
        return chunkResult(text)
    }

    override suspend fun streamKey(key: String): Boolean {
        calls += "key:$key"
        return keyResult(key)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PcLiveTypingControllerTest {
    @Test
    fun serializesRapidDesiredTextUpdates() = runTest {
        val releaseFirst = CompletableDeferred<Boolean>()
        var chunks = 0
        val stream = RecordingStream(chunkResult = { if (++chunks == 1) releaseFirst.await() else true })
        val controller = PcLiveTypingController(stream)
        val one = async { controller.update("a") }
        runCurrent()
        val two = async { controller.update("ab") }
        runCurrent()
        releaseFirst.complete(true)
        one.await()
        two.await()
        assertEquals("ab", stream.calls.joinToString("") { it.removePrefix("chunk:") })
        assertEquals("ab", controller.applied())
    }

    @Test
    fun usesCodePointsForDeletionAndSupportsImeReplacement() = runTest {
        val stream = RecordingStream()
        val controller = PcLiveTypingController(stream)
        controller.update("🙂")
        controller.update("")
        controller.update("cafe")
        controller.update("café")
        assertEquals(
            listOf("chunk:🙂", "key:Backspace", "chunk:cafe", "key:Backspace", "chunk:é"),
            stream.calls
        )
    }

    @Test
    fun preservesUnappliedTextForRetryAfterFailure() = runTest {
        var attempt = 0
        val stream = RecordingStream(chunkResult = { ++attempt > 1 })
        val controller = PcLiveTypingController(stream)
        assertFalse(controller.update("unsent"))
        assertEquals("", controller.applied())
        assertTrue(controller.update("unsent"))
        assertEquals("unsent", controller.applied())
    }

    @Test
    fun reconcilesPendingTextBeforeEnterAndResetsBaseline() = runTest {
        val releaseChunk = CompletableDeferred<Boolean>()
        val stream = RecordingStream(chunkResult = { text -> if (text == "first") releaseChunk.await() else true })
        val controller = PcLiveTypingController(stream)
        val update = async { controller.update("first") }
        val submit = async { controller.submitLine() }
        runCurrent()
        releaseChunk.complete(true)
        assertTrue(update.await())
        assertTrue(submit.await())
        assertEquals(listOf("chunk:first", "key:Enter"), stream.calls)
        assertEquals("", controller.applied())

        assertTrue(controller.update("second"))
        assertEquals(listOf("chunk:first", "key:Enter", "chunk:second"), stream.calls)
    }

    @Test
    fun retriesFailedReconciliationBeforeEnter() = runTest {
        var attempt = 0
        val stream = RecordingStream(chunkResult = { ++attempt > 1 })
        val controller = PcLiveTypingController(stream)
        assertFalse(controller.update("retry me"))
        assertTrue(controller.submitLine())
        assertEquals(listOf("chunk:retry me", "chunk:retry me", "key:Enter"), stream.calls)
        assertEquals("", controller.applied())
    }

    @Test
    fun keepsBaselineWhenEnterFailsSoRetrySendsNoDuplicateText() = runTest {
        var enterAttempt = 0
        val stream = RecordingStream(keyResult = { ++enterAttempt > 1 })
        val controller = PcLiveTypingController(stream)
        controller.update("kept")
        assertFalse(controller.submitLine())
        assertEquals("kept", controller.applied())
        assertTrue(controller.submitLine())
        assertEquals(listOf("chunk:kept", "key:Enter", "key:Enter"), stream.calls)
        assertEquals("", controller.applied())
    }
}
