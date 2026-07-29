package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PC_TEXT_STREAM_CHUNK_MAX_CHARS
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcTextStreamItem
import com.enaboapps.switchify.pc.isSafePcTypedText
import com.enaboapps.switchify.pc.pcTextStreamItemsFor
import java.text.BreakIterator
import java.util.Locale
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
    private val onFailure: (String) -> Unit = {},
    private val onResumed: () -> Unit = {}
) {
    private val lock = Any()
    private val operations = ArrayDeque<PcLiveTypingOperation>()
    private var worker: Job? = null
    private var accepting = false
    private var paused = false
    private var streamId: String? = null
    private var nextSequence = 0
    private var reopenStreamOnResume = false
    private var retainFailuresWhenStopped = true
    private var awaitingResumeAcknowledgement = false

    fun start() {
        synchronized(lock) {
            accepting = true
            retainFailuresWhenStopped = true
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

    fun submitEditorChange(previousText: String, currentText: String): Boolean {
        if (!isSafePcTypedText(currentText)) return false
        val operationsToAppend = editorOperations(previousText, currentText)
        synchronized(lock) {
            if (!accepting || paused) return false
            operationsToAppend.forEach(::appendOperationLocked)
            scheduleLocked()
        }
        return true
    }

    fun finish() {
        synchronized(lock) {
            if (!accepting && operations.isEmpty() && streamId == null) return
            if (!accepting && operations.lastOrNull() == PcLiveTypingOperation.Finish) return
            accepting = false
            retainFailuresWhenStopped = true
            if (operations.lastOrNull() != PcLiveTypingOperation.Finish) {
                operations.addLast(PcLiveTypingOperation.Finish)
            }
            scheduleLocked()
        }
    }

    fun resume() {
        synchronized(lock) {
            if (!paused) return
            if (reopenStreamOnResume) {
                streamId = null
                nextSequence = 0
                reopenStreamOnResume = false
            }
            paused = false
            awaitingResumeAcknowledgement = true
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

    private fun appendOperationLocked(operation: PcLiveTypingOperation) {
        when (operation) {
            is PcLiveTypingOperation.Text -> appendTextLocked(operation.value)
            is PcLiveTypingOperation.Key -> operations.addLast(operation)
            PcLiveTypingOperation.Finish -> operations.addLast(operation)
        }
    }

    fun endSession() {
        synchronized(lock) {
            accepting = false
            retainFailuresWhenStopped = false
            awaitingResumeAcknowledgement = false
            if (paused) {
                operations.clear()
                paused = false
                streamId = null
                nextSequence = 0
                reopenStreamOnResume = false
                return
            }
            if (operations.lastOrNull() != PcLiveTypingOperation.Finish) {
                operations.addLast(PcLiveTypingOperation.Finish)
            }
            scheduleLocked()
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
                requeueAndPause(operation, reopenStream = true)
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
                    val resumed = synchronized(lock) {
                        if (awaitingResumeAcknowledgement) {
                            awaitingResumeAcknowledgement = false
                            true
                        } else {
                            false
                        }
                    }
                    if (resumed) onResumed()
                }
                is PcCommandResult.AuthFailed -> {
                    requeueAndPause(operation, result.message)
                    break
                }
                is PcCommandResult.Failed -> {
                    requeueAndPause(
                        operation = operation,
                        reopenStream = result.isMissingTextStream()
                    )
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
        message: String = LIVE_TYPING_FAILED_MESSAGE,
        reopenStream: Boolean = false
    ) {
        val shouldNotify = synchronized(lock) {
            if (!accepting && !retainFailuresWhenStopped) {
                operations.clear()
                paused = false
                streamId = null
                nextSequence = 0
                reopenStreamOnResume = false
                awaitingResumeAcknowledgement = false
                false
            } else {
                operations.addFirst(operation)
                paused = true
                reopenStreamOnResume = reopenStream
                true
            }
        }
        if (shouldNotify) {
            onFailure(message)
        }
    }

    private fun PcCommandResult.Failed.isMissingTextStream(): Boolean {
        return message.equals(TEXT_STREAM_NOT_OPEN_MESSAGE, ignoreCase = true)
    }

    private fun editorOperations(
        previousText: String,
        currentText: String
    ): List<PcLiveTypingOperation> {
        val previousGraphemes = previousText.graphemes()
        val currentGraphemes = currentText.graphemes()
        val commonPrefixLength = previousGraphemes
            .zip(currentGraphemes)
            .takeWhile { (previous, current) -> previous == current }
            .size
        val operations = mutableListOf<PcLiveTypingOperation>()
        repeat(previousGraphemes.size - commonPrefixLength) {
            operations += PcLiveTypingOperation.Key(PcKeyboardKey.Backspace)
        }
        val replacement = currentGraphemes
            .drop(commonPrefixLength)
            .joinToString(separator = "")
        pcTextStreamItemsFor(replacement).forEach { item ->
            operations += when (item) {
                is PcTextStreamItem.Chunk -> PcLiveTypingOperation.Text(item.text)
                is PcTextStreamItem.Key -> PcLiveTypingOperation.Key(item.key)
            }
        }
        return operations
    }

    private fun String.graphemes(): List<String> {
        if (isEmpty()) return emptyList()
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(this)
        val graphemes = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            graphemes += substring(start, end)
            start = end
            end = iterator.next()
        }
        return graphemes
    }

    companion object {
        const val MAX_STREAM_ITEMS = 2_000
        const val LIVE_TYPING_FAILED_MESSAGE =
            "Live typing paused. Some input may not have reached the PC."
        private const val TEXT_STREAM_NOT_OPEN_MESSAGE = "Text stream is not open."
    }
}
