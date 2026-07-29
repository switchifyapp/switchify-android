package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PC_TEXT_STREAM_CHUNK_MAX_CHARS
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcTextStreamItem
import com.enaboapps.switchify.pc.isSafePcTypedText
import com.enaboapps.switchify.pc.pcTextStreamItemsFor
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal sealed class PcLiveTypingOperation {
    data class Text(val value: String) : PcLiveTypingOperation()
    data class Key(val value: PcKeyboardKey) : PcLiveTypingOperation()
    data object Finish : PcLiveTypingOperation()
}

internal class PcLiveTypingCoordinator(
    private val scope: CoroutineScope,
    private val sendCommand: suspend (PcControlCommand) -> PcCommandResult,
    private val streamIdProvider: () -> String = { "android-${UUID.randomUUID()}" },
    private val onFailure: (String) -> Unit = {}
) {
    private val lock = Any()
    private val operations = ArrayDeque<PcLiveTypingOperation>()
    private var worker: Job? = null
    private var accepting = false
    private var paused = false
    private var streamId: String? = null
    private var nextSequence = 0

    fun start() {
        synchronized(lock) {
            accepting = true
            paused = false
            scheduleLocked()
        }
    }

    fun submitText(text: String): Boolean {
        if (text.isEmpty() || !isSafePcTypedText(text)) return false
        val items = pcTextStreamItemsFor(text)
        synchronized(lock) {
            if (!accepting || paused) return false
            items.forEach { item ->
                when (item) {
                    is PcTextStreamItem.Chunk -> appendTextLocked(item.text)
                    is PcTextStreamItem.Key -> operations.addLast(PcLiveTypingOperation.Key(item.key))
                }
            }
            scheduleLocked()
        }
        return true
    }

    fun submitKey(key: PcKeyboardKey): Boolean {
        synchronized(lock) {
            if (!accepting || paused) return false
            operations.addLast(PcLiveTypingOperation.Key(key))
            scheduleLocked()
        }
        return true
    }

    fun finish() {
        synchronized(lock) {
            if (!accepting && operations.isEmpty() && streamId == null) return
            if (!accepting && operations.lastOrNull() == PcLiveTypingOperation.Finish) return
            accepting = false
            if (operations.lastOrNull() != PcLiveTypingOperation.Finish) {
                operations.addLast(PcLiveTypingOperation.Finish)
            }
            paused = false
            scheduleLocked()
        }
    }

    fun resume() {
        synchronized(lock) {
            if (!paused) return
            paused = false
            scheduleLocked()
        }
    }

    private fun appendTextLocked(text: String) {
        val tail = operations.lastOrNull() as? PcLiveTypingOperation.Text
        if (tail != null && tail.value.length + text.length <= PC_TEXT_STREAM_CHUNK_MAX_CHARS) {
            operations.removeLast()
            operations.addLast(PcLiveTypingOperation.Text(tail.value + text))
        } else {
            operations.addLast(PcLiveTypingOperation.Text(text))
        }
    }

    private fun scheduleLocked() {
        if (paused || worker?.isActive == true || operations.isEmpty()) return
        worker = scope.launch { processOperations() }
    }

    private suspend fun processOperations() {
        while (true) {
            val operation = synchronized(lock) {
                if (paused || operations.isEmpty()) null else operations.removeFirst()
            } ?: break

            if (operation == PcLiveTypingOperation.Finish) {
                closeCurrentStream()
                streamId = null
                nextSequence = 0
                continue
            }

            if (nextSequence >= MAX_STREAM_ITEMS && !closeCurrentStream()) {
                requeueAndPause(operation)
                break
            }

            val activeStreamId = ensureStreamOpen()
            if (activeStreamId == null) {
                requeueAndPause(operation)
                break
            }

            val command = when (operation) {
                is PcLiveTypingOperation.Text ->
                    PcControlCommand.TextStreamChunk(activeStreamId, nextSequence, operation.value)
                is PcLiveTypingOperation.Key ->
                    PcControlCommand.TextStreamKey(activeStreamId, nextSequence, operation.value)
                PcLiveTypingOperation.Finish -> continue
            }

            when (val result = sendCommand(command)) {
                PcCommandResult.Ack -> {
                    nextSequence += 1
                }
                is PcCommandResult.AuthFailed -> {
                    requeueAndPause(operation, result.message)
                    break
                }
                is PcCommandResult.Failed -> {
                    requeueAndPause(operation)
                    break
                }
            }
        }

        synchronized(lock) {
            worker = null
            scheduleLocked()
        }
    }

    private suspend fun ensureStreamOpen(): String? {
        streamId?.let { return it }
        val newStreamId = streamIdProvider()
        return when (sendCommand(PcControlCommand.TextStreamOpen(newStreamId))) {
            PcCommandResult.Ack -> {
                streamId = newStreamId
                nextSequence = 0
                newStreamId
            }
            else -> null
        }
    }

    private suspend fun closeCurrentStream(): Boolean {
        val activeStreamId = streamId ?: return true
        val expectedCount = nextSequence
        val result = sendCommand(PcControlCommand.TextStreamClose(activeStreamId, expectedCount))
        if (result is PcCommandResult.Ack) {
            streamId = null
            nextSequence = 0
            return true
        }
        return false
    }

    private fun requeueAndPause(
        operation: PcLiveTypingOperation,
        message: String = LIVE_TYPING_FAILED_MESSAGE
    ) {
        synchronized(lock) {
            operations.addFirst(operation)
            paused = true
        }
        onFailure(message)
    }

    companion object {
        const val MAX_STREAM_ITEMS = 2_000
        const val LIVE_TYPING_FAILED_MESSAGE =
            "Live typing paused. Some input may not have reached the PC."
    }
}
