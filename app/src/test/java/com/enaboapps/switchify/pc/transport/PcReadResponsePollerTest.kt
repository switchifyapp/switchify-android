package com.enaboapps.switchify.pc.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcReadResponsePollerTest {
    @Test
    fun pollsSeriallyIdlesOnEmptyValuesAndStopsWithoutLateDelivery() = runTest {
        val finish = CompletableDeferred<ByteArray?>()
        var reads = 0
        var cancels = 0
        val frames = mutableListOf<ByteArray>()
        val errors = mutableListOf<Throwable>()
        val poller = PcReadResponsePoller(
            scope = backgroundScope,
            read = {
                reads += 1
                if (reads == 1) ByteArray(0) else finish.await()
            },
            cancelRead = { cancels += 1 },
            onFrame = { frames += it },
            onError = { errors += it },
            timeoutMs = 1_000
        )
        poller.ready.await()
        advanceTimeBy(99)
        runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, reads)
        poller.stop()
        finish.complete("{}".toByteArray())
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(frames.isEmpty())
        assertTrue(errors.isEmpty())
        assertEquals(1, cancels)
        assertEquals(2, reads)
    }

    @Test
    fun deliversNonEmptyFramesWithoutTheIdleDelay() = runTest {
        var reads = 0
        val frames = mutableListOf<String>()
        val poller = PcReadResponsePoller(
            scope = backgroundScope,
            read = {
                reads += 1
                if (reads == 1) "{}".toByteArray() else ByteArray(0)
            },
            cancelRead = {},
            onFrame = { frames += it.toString(Charsets.UTF_8) },
            onError = {},
            timeoutMs = 1_000
        )
        poller.ready.await()
        runCurrent()
        assertEquals(listOf("{}"), frames)
        assertEquals(2, reads)
        poller.stop()
    }

    @Test
    fun failsClosedOnMissingOrOversizedValues() = runTest {
        listOf<ByteArray?>(null, ByteArray(181)).forEach { value ->
            val errors = mutableListOf<Throwable>()
            val poller = PcReadResponsePoller(backgroundScope, { value }, {}, {}, { errors += it }, 1_000)
            val ready = runCatching { poller.ready.await() }
            assertTrue(ready.isFailure)
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(1, errors.size)
            assertEquals("Bluetooth response reading failed. Reconnect to the PC.", errors.single().message)
        }
    }

    @Test
    fun acceptsTheLargestReadFrame() = runTest {
        val frames = mutableListOf<ByteArray>()
        var reads = 0
        val poller = PcReadResponsePoller(
            backgroundScope,
            { reads += 1; if (reads == 1) ByteArray(180) else ByteArray(0) },
            {},
            { frames += it },
            {},
            1_000
        )
        poller.ready.await()
        runCurrent()
        assertEquals(180, frames.single().size)
        poller.stop()
    }

    @Test
    fun timesOutOnceCancelsTheReadAndIgnoresItsLateCompletion() = runTest {
        val finish = CompletableDeferred<ByteArray?>()
        var cancels = 0
        val frames = mutableListOf<ByteArray>()
        val errors = mutableListOf<Throwable>()
        val poller = PcReadResponsePoller(backgroundScope, { finish.await() }, { cancels += 1 }, { frames += it }, { errors += it }, 1_000)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(runCatching { poller.ready.await() }.isFailure)
        finish.complete("{}".toByteArray())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, errors.size)
        assertEquals(1, cancels)
        assertTrue(frames.isEmpty())
    }
}
