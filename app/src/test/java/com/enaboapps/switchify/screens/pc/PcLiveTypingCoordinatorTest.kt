package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcKeyboardKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcLiveTypingCoordinatorTest {
    @Test
    fun opensLazilyAndSendsCommittedInputInOrder() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val coordinator = coordinator(commands)

        coordinator.start()
        advanceUntilIdle()

        assertTrue(commands.isEmpty())

        assertTrue(coordinator.submitText("Hello\n"))
        coordinator.submitKey(PcKeyboardKey.Backspace)
        coordinator.finish()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.TextStreamOpen("stream-1"),
                PcControlCommand.TextStreamChunk("stream-1", 0, "Hello"),
                PcControlCommand.TextStreamKey("stream-1", 1, PcKeyboardKey.Enter),
                PcControlCommand.TextStreamKey("stream-1", 2, PcKeyboardKey.Backspace),
                PcControlCommand.TextStreamClose("stream-1", 3)
            ),
            commands
        )
    }

    @Test
    fun coalescesAdjacentTextWithoutBreakingUnicode() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val coordinator = coordinator(commands)

        coordinator.start()
        coordinator.submitText("Hello ")
        coordinator.submitText("👋")
        coordinator.finish()
        advanceUntilIdle()

        assertEquals(
            listOf(PcControlCommand.TextStreamChunk("stream-1", 0, "Hello 👋")),
            commands.filterIsInstance<PcControlCommand.TextStreamChunk>()
        )
    }

    @Test
    fun preservesTextQueuedWhilePreviousChunkAwaitsAcknowledgement() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val firstChunkStarted = CompletableDeferred<Unit>()
        val firstChunkResult = CompletableDeferred<PcCommandResult>()
        var chunkCount = 0
        val coordinator = PcLiveTypingCoordinator(
            scope = this,
            sendCommand = { command ->
                commands += command
                if (command is PcControlCommand.TextStreamChunk && chunkCount++ == 0) {
                    firstChunkStarted.complete(Unit)
                    firstChunkResult.await()
                } else {
                    PcCommandResult.Ack
                }
            },
            streamIdProvider = { "stream-1" }
        )

        coordinator.start()
        coordinator.submitText("A")
        runCurrent()
        firstChunkStarted.await()

        coordinator.submitText("B")
        coordinator.submitText("C")
        firstChunkResult.complete(PcCommandResult.Ack)
        coordinator.finish()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.TextStreamChunk("stream-1", 0, "A"),
                PcControlCommand.TextStreamChunk("stream-1", 1, "BC")
            ),
            commands.filterIsInstance<PcControlCommand.TextStreamChunk>()
        )
    }

    @Test
    fun rollsOverBeforeProtocolItemLimit() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        var streamNumber = 0
        val coordinator = PcLiveTypingCoordinator(
            scope = this,
            sendCommand = {
                commands += it
                PcCommandResult.Ack
            },
            streamIdProvider = { "stream-${++streamNumber}" }
        )

        coordinator.start()
        repeat(PcLiveTypingCoordinator.MAX_STREAM_ITEMS + 1) {
            coordinator.submitKey(PcKeyboardKey.Backspace)
        }
        coordinator.finish()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.TextStreamClose(
                    "stream-1",
                    PcLiveTypingCoordinator.MAX_STREAM_ITEMS
                ),
                PcControlCommand.TextStreamClose("stream-2", 1)
            ),
            commands.filterIsInstance<PcControlCommand.TextStreamClose>()
        )
        assertEquals(
            listOf(
                PcControlCommand.TextStreamOpen("stream-1"),
                PcControlCommand.TextStreamOpen("stream-2")
            ),
            commands.filterIsInstance<PcControlCommand.TextStreamOpen>()
        )
    }

    @Test
    fun retriesTheSameSequenceAfterResume() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val failures = mutableListOf<String>()
        var failFirstChunk = true
        val coordinator = PcLiveTypingCoordinator(
            scope = this,
            sendCommand = { command ->
                commands += command
                if (command is PcControlCommand.TextStreamChunk && failFirstChunk) {
                    failFirstChunk = false
                    PcCommandResult.Failed()
                } else {
                    PcCommandResult.Ack
                }
            },
            streamIdProvider = { "stream-1" },
            onFailure = failures::add
        )

        coordinator.start()
        coordinator.submitText("A")
        advanceUntilIdle()

        assertFalse(coordinator.submitText("B"))
        assertEquals(1, failures.size)

        coordinator.resume()
        coordinator.finish()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.TextStreamChunk("stream-1", 0, "A"),
                PcControlCommand.TextStreamChunk("stream-1", 0, "A")
            ),
            commands.filterIsInstance<PcControlCommand.TextStreamChunk>()
        )
        assertEquals(
            listOf(PcControlCommand.TextStreamClose("stream-1", 1)),
            commands.filterIsInstance<PcControlCommand.TextStreamClose>()
        )
    }

    @Test
    fun retryReopensAStreamThePcNoLongerHas() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        var streamNumber = 0
        var missingStreamReported = false
        val coordinator = PcLiveTypingCoordinator(
            scope = this,
            sendCommand = { command ->
                commands += command
                if (command is PcControlCommand.TextStreamChunk && !missingStreamReported) {
                    missingStreamReported = true
                    PcCommandResult.Failed(message = "Text stream is not open.")
                } else {
                    PcCommandResult.Ack
                }
            },
            streamIdProvider = { "stream-${++streamNumber}" }
        )

        coordinator.start()
        coordinator.submitText("A")
        advanceUntilIdle()

        coordinator.resume()
        coordinator.finish()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PcControlCommand.TextStreamOpen("stream-1"),
                PcControlCommand.TextStreamOpen("stream-2")
            ),
            commands.filterIsInstance<PcControlCommand.TextStreamOpen>()
        )
        assertEquals(
            listOf(
                PcControlCommand.TextStreamChunk("stream-1", 0, "A"),
                PcControlCommand.TextStreamChunk("stream-2", 0, "A")
            ),
            commands.filterIsInstance<PcControlCommand.TextStreamChunk>()
        )
    }

    @Test
    fun recreatingTheComposerDoesNotResumeAFailedOperation() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        var failFirstChunk = true
        val coordinator = PcLiveTypingCoordinator(
            scope = this,
            sendCommand = { command ->
                commands += command
                if (command is PcControlCommand.TextStreamChunk && failFirstChunk) {
                    failFirstChunk = false
                    PcCommandResult.Failed()
                } else {
                    PcCommandResult.Ack
                }
            },
            streamIdProvider = { "stream-1" }
        )

        coordinator.start()
        coordinator.submitText("A")
        advanceUntilIdle()
        coordinator.start()
        advanceUntilIdle()

        assertEquals(
            1,
            commands.filterIsInstance<PcControlCommand.TextStreamChunk>().size
        )

        coordinator.resume()
        advanceUntilIdle()

        assertEquals(
            2,
            commands.filterIsInstance<PcControlCommand.TextStreamChunk>().size
        )
    }

    @Test
    fun repeatedFinishClosesAnOpenStreamOnlyOnce() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val coordinator = coordinator(commands)

        coordinator.start()
        coordinator.submitText("A")
        coordinator.finish()
        coordinator.finish()
        advanceUntilIdle()

        assertEquals(
            1,
            commands.filterIsInstance<PcControlCommand.TextStreamClose>().size
        )
    }

    @Test
    fun rejectsUnsafeOrInactiveText() = runTest {
        val commands = mutableListOf<PcControlCommand>()
        val coordinator = coordinator(commands)

        assertFalse(coordinator.submitText("Not started"))
        coordinator.start()
        assertFalse(coordinator.submitText("Unsafe\u001B"))
        assertFalse(coordinator.submitText(""))
        advanceUntilIdle()

        assertTrue(commands.isEmpty())
    }

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        commands: MutableList<PcControlCommand>
    ): PcLiveTypingCoordinator {
        return PcLiveTypingCoordinator(
            scope = this,
            sendCommand = {
                commands += it
                PcCommandResult.Ack
            },
            streamIdProvider = { "stream-1" }
        )
    }
}
