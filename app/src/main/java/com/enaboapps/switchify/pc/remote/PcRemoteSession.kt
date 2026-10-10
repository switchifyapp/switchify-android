package com.enaboapps.switchify.pc.remote

import com.enaboapps.switchify.pc.connection.PcSendOutcome
import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcCommandTypes
import com.enaboapps.switchify.pc.protocol.PcCommands
import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.protocol.PcRepeatCommand
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class PcRemoteSessionState(
    val repeat: String? = null,
    val dragging: Boolean = false,
    val modifiers: List<String> = emptyList(),
    val streamOpen: Boolean = false
) {
    val repeatingKey: Boolean get() = repeat == PcCommandTypes.KEYBOARD_KEY
}

fun interface PcRemoteSender {
    suspend fun send(command: PcCommand, responseMode: PcResponseMode): PcSendOutcome
}

interface PcLiveTypingStream {
    suspend fun streamChunk(text: String): Boolean

    suspend fun streamKey(key: String): Boolean
}

class PcRemoteSession(
    private val sender: PcRemoteSender,
    private val profileProvider: () -> PcPointerProfile?,
    private val scope: CoroutineScope,
    private val switchStop: PcRepeatSwitchStop = PcRepeatSwitchStop.None,
    private val streamIds: PcIdGenerator = PcIdGenerator { "stream-${UUID.randomUUID()}" }
) : PcLiveTypingStream {
    private val _state = MutableStateFlow(PcRemoteSessionState())
    val state: StateFlow<PcRemoteSessionState> = _state.asStateFlow()

    val profile: PcPointerProfile? get() = profileProvider()

    private val queue = PcSerialQueue()
    private var streamId: String? = null
    private var sequence = 0
    private var repeatingKey: String? = null
    private var switchStopHandle: PcSwitchStopHandle? = null
    private var switchStopGeneration = 0
    private var epoch = 0
    private var closed = false
    private var stopPending = false
    private var repeatMayBeActive = false

    fun snapshot(): PcRemoteSessionState = _state.value

    fun supports(type: String): Boolean = profile?.capabilities?.supports(type) == true

    fun supportsAll(vararg types: String): Boolean = types.all(::supports)

    suspend fun mouse(command: PcCommand, repeatable: Boolean = false): Boolean =
        enqueue(false) { mouseNow(command, repeatable) }

    suspend fun key(key: String): Boolean = enqueue(false) { keyNow(key) }

    suspend fun stopRepeat() {
        if (!reserveRepeatStop()) {
            queue.idle()
            return
        }
        queue.enqueue { completeRepeatStop() }
    }

    suspend fun toggleDrag(): Boolean = enqueue(false) {
        stopRepeatNow()
        val dragging = _state.value.dragging
        val command = if (dragging) PcCommands.dragEnd() else PcCommands.dragStart()
        if (!supports(command.type)) return@enqueue false
        when (sender.send(command, PcResponseMode.Ack)) {
            PcSendOutcome.Accepted -> {
                update { copy(dragging = !dragging) }
                true
            }
            PcSendOutcome.Unconfirmed -> {
                if (!dragging) undo(PcCommands.dragEnd())
                false
            }
            PcSendOutcome.Rejected -> false
        }
    }

    suspend fun toggleModifier(key: String): Boolean = enqueue(false) { toggleModifierNow(key) }

    suspend fun command(command: PcCommand): Boolean = enqueue(false) { commandNow(command) }

    suspend fun openStream(): Boolean = enqueue(false) { openStreamNow() }

    override suspend fun streamChunk(text: String): Boolean {
        if (text.isEmpty()) return false
        return enqueue(false) {
            if (!supports(PcCommandTypes.KEYBOARD_STREAM_CHUNK) || !openStreamNow()) return@enqueue false
            val id = streamId ?: return@enqueue false
            stopRepeatNow()
            val command = PcCommands.streamChunk(id, sequence, text)
            val ok = sendAccepted(command, responseModeFor(command.type))
            if (ok) sequence += 1
            ok
        }
    }

    override suspend fun streamKey(key: String): Boolean = enqueue(false) {
        if (!supports(PcCommandTypes.KEYBOARD_STREAM_KEY) || !openStreamNow()) return@enqueue false
        val id = streamId ?: return@enqueue false
        stopRepeatNow()
        val ok = sendAccepted(PcCommands.streamKey(id, sequence, key), PcResponseMode.Ack)
        if (ok) sequence += 1
        ok
    }

    suspend fun closeStream() {
        enqueue(Unit) {
            stopRepeatNow()
            closeStreamNow()
        }
    }

    suspend fun shortcut(key: String): Boolean = enqueue(false) {
        if (!supports(PcCommandTypes.KEYBOARD_SHORTCUT)) return@enqueue false
        val active = _state.value.modifiers.toList()
        if (!commandNow(PcCommands.shortcut(active + key))) return@enqueue false
        for (modifier in active) {
            val release = PcCommands.modifierUp(modifier)
            if (supports(release.type)) sender.send(release, responseModeFor(release.type))
        }
        if (active.isNotEmpty()) update { copy(modifiers = emptyList()) }
        true
    }

    suspend fun cleanup() = releaseInput(final = false)

    suspend fun close() = releaseInput(final = true)

    fun dispose() {
        closed = true
        epoch += 1
        releaseSwitchStop()
    }

    private suspend fun releaseInput(final: Boolean) {
        epoch += 1
        if (final) closed = true
        reserveRepeatStop()
        queue.enqueue {
            val stopAttempted = stopRepeatNow()
            if (!stopAttempted && repeatMayBeActive && repeatCommandsSupported()) sendRepeatStop()
            if (_state.value.dragging) sendQuietly(PcCommands.dragEnd())
            for (key in _state.value.modifiers) sendQuietly(PcCommands.modifierUp(key))
            closeStreamNow()
            releaseSwitchStop()
            repeatingKey = null
            update { copy(repeat = null, dragging = false, modifiers = emptyList(), streamOpen = false) }
        }
    }

    private suspend fun <T> enqueue(skipped: T, block: suspend () -> T): T {
        val requested = epoch
        return queue.enqueue {
            if (closed || requested != epoch) skipped else block()
        }
    }

    private suspend fun commandNow(command: PcCommand): Boolean =
        commandOutcomeNow(command) == PcSendOutcome.Accepted

    private suspend fun commandOutcomeNow(command: PcCommand): PcSendOutcome {
        if (!supports(command.type)) return PcSendOutcome.Rejected
        stopRepeatNow()
        val outcome = sender.send(command, responseModeFor(command.type))
        if (outcome == PcSendOutcome.Accepted && _state.value.dragging && command.type in CLICK_TYPES) {
            update { copy(dragging = false) }
        }
        return outcome
    }

    private suspend fun mouseNow(command: PcCommand, repeatable: Boolean): Boolean {
        if (!supports(command.type)) return false
        if (_state.value.repeat != null || stopPending) {
            stopRepeatNow()
            return true
        }
        val repeatCommand = if (repeatable) repeatCommandFor(command) else null
        val mouseRepeat = profile?.capabilities?.mouseRepeat
        if (repeatCommand != null && repeatCommandsSupported() && mouseRepeat?.supported == true && mouseRepeat.enabled) {
            return startRepeat(PcCommands.repeatStart(repeatCommand), command.type, null)
        }
        return sendAccepted(command, responseModeFor(command.type))
    }

    private suspend fun keyNow(key: String): Boolean {
        if (!supports(PcCommandTypes.KEYBOARD_KEY)) return false
        val current = _state.value
        if (current.repeat != null || stopPending) {
            val ownRepeat = current.repeat == PcCommandTypes.KEYBOARD_KEY && repeatingKey == key
            stopRepeatNow()
            if (ownRepeat) return true
            return sendKey(key)
        }
        if (repeatableKey(key)) {
            return startRepeat(PcCommands.repeatStart(PcRepeatCommand.Key(key)), PcCommandTypes.KEYBOARD_KEY, key)
        }
        return sendKey(key)
    }

    private suspend fun startRepeat(start: PcCommand, type: String, key: String?): Boolean {
        val outcome = sender.send(start, PcResponseMode.Ack)
        if (outcome != PcSendOutcome.Rejected) repeatMayBeActive = true
        return when (outcome) {
            PcSendOutcome.Accepted -> {
                repeatingKey = key
                update { copy(repeat = type) }
                armSwitchStop()
                true
            }
            PcSendOutcome.Unconfirmed -> {
                sendRepeatStop()
                false
            }
            PcSendOutcome.Rejected -> false
        }
    }

    private suspend fun sendKey(key: String): Boolean {
        val command = PcCommands.key(key)
        return sendAccepted(command, responseModeFor(command.type))
    }

    private suspend fun toggleModifierNow(key: String): Boolean {
        val active = key in _state.value.modifiers
        val command = if (active) PcCommands.modifierUp(key) else PcCommands.modifierDown(key)
        if (!supports(command.type)) return false
        stopRepeatNow()
        return when (sender.send(command, responseModeFor(command.type))) {
            PcSendOutcome.Accepted -> {
                update { copy(modifiers = if (active) modifiers - key else modifiers + key) }
                true
            }
            PcSendOutcome.Unconfirmed -> {
                if (!active) undo(PcCommands.modifierUp(key))
                false
            }
            PcSendOutcome.Rejected -> false
        }
    }

    private suspend fun openStreamNow(): Boolean {
        if (_state.value.streamOpen && streamId != null) return true
        if (!supports(PcCommandTypes.KEYBOARD_STREAM_OPEN) || !supports(PcCommandTypes.KEYBOARD_STREAM_CLOSE)) return false
        val id = streamIds.nextId()
        return when (commandOutcomeNow(PcCommands.streamOpen(id))) {
            PcSendOutcome.Accepted -> {
                streamId = id
                sequence = 0
                update { copy(streamOpen = true) }
                true
            }
            PcSendOutcome.Unconfirmed -> {
                sendQuietly(PcCommands.streamClose(id, 0), PcResponseMode.Ack)
                false
            }
            PcSendOutcome.Rejected -> false
        }
    }

    private suspend fun closeStreamNow() {
        val id = streamId
        streamId = null
        if (!_state.value.streamOpen || id == null) return
        update { copy(streamOpen = false) }
        sendQuietly(PcCommands.streamClose(id, sequence), PcResponseMode.Ack)
    }

    private suspend fun stopRepeatNow(): Boolean {
        reserveRepeatStop()
        return completeRepeatStop()
    }

    private fun repeatableKey(key: String): Boolean {
        val keyRepeat = profile?.capabilities?.keyRepeat ?: return false
        return repeatCommandsSupported() &&
            keyRepeat.supported &&
            keyRepeat.enabled &&
            key in keyRepeat.repeatableKeys &&
            key in REPEATABLE_KEYS
    }

    private fun repeatCommandsSupported() =
        supports(PcCommandTypes.MOUSE_REPEAT_START) && supports(PcCommandTypes.MOUSE_REPEAT_STOP)

    private fun repeatCommandFor(command: PcCommand): PcRepeatCommand? {
        val dx = (command.payload["dx"] as? Number)?.toDouble() ?: return null
        val dy = (command.payload["dy"] as? Number)?.toDouble() ?: return null
        return when (command.type) {
            PcCommandTypes.MOUSE_MOVE -> PcRepeatCommand.Move(dx, dy)
            PcCommandTypes.MOUSE_SCROLL -> PcRepeatCommand.Scroll(dx, dy)
            else -> null
        }
    }

    private fun reserveRepeatStop(): Boolean {
        if (_state.value.repeat == null) return false
        repeatingKey = null
        stopPending = true
        update { copy(repeat = null) }
        return true
    }

    private suspend fun completeRepeatStop(): Boolean {
        if (!stopPending) return false
        stopPending = false
        try {
            sendRepeatStop()
        } finally {
            releaseSwitchStop()
        }
        return true
    }

    private suspend fun undo(command: PcCommand) {
        if (supports(command.type)) sendQuietly(command, responseModeFor(command.type))
    }

    private suspend fun sendRepeatStop() {
        if (sendQuietly(PcCommands.repeatStop(), PcResponseMode.Ack) == PcSendOutcome.Accepted) {
            repeatMayBeActive = false
        }
    }

    private fun armSwitchStop() {
        releaseSwitchStop()
        val generation = ++switchStopGeneration
        switchStopHandle = switchStop.arm {
            scope.launch {
                if (generation == switchStopGeneration && _state.value.repeat != null) stopRepeat()
            }
        }
    }

    private fun releaseSwitchStop() {
        switchStopGeneration += 1
        switchStopHandle?.release()
        switchStopHandle = null
    }

    private suspend fun sendAccepted(command: PcCommand, responseMode: PcResponseMode): Boolean =
        sender.send(command, responseMode) == PcSendOutcome.Accepted

    private suspend fun sendQuietly(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.None): PcSendOutcome =
        try {
            sender.send(command, responseMode)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PcSendOutcome.Rejected
        }

    private fun responseModeFor(type: String): PcResponseMode {
        val capabilities = profile?.capabilities ?: return PcResponseMode.Ack
        val noAck = capabilities.supportsNoAck(type) ||
            (type == PcCommandTypes.MOUSE_MOVE && capabilities.noAckMouseMove)
        return if (noAck) PcResponseMode.None else PcResponseMode.Ack
    }

    private inline fun update(transform: PcRemoteSessionState.() -> PcRemoteSessionState) {
        _state.value = _state.value.transform()
    }

    companion object {
        val REPEATABLE_KEYS = listOf(
            "ArrowUp",
            "ArrowDown",
            "ArrowLeft",
            "ArrowRight",
            "Tab",
            "Backspace",
            "Delete",
            "PageUp",
            "PageDown"
        )

        private val CLICK_TYPES = setOf(
            PcCommandTypes.MOUSE_CLICK,
            PcCommandTypes.MOUSE_DOUBLE_CLICK,
            PcCommandTypes.MOUSE_RIGHT_CLICK
        )
    }
}
