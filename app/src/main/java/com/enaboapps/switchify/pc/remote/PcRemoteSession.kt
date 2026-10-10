package com.enaboapps.switchify.pc.remote

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

    val editingBlocked: Boolean get() = repeat != null || dragging || modifiers.isNotEmpty()
}

fun interface PcRemoteSender {
    suspend fun send(command: PcCommand, responseMode: PcResponseMode): Boolean
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

    private val streamQueue = PcSerialQueue()
    private val repeatQueue = PcSerialQueue()
    private var streamId: String? = null
    private var sequence = 0
    private var repeatingKey: String? = null
    private var switchStopHandle: PcSwitchStopHandle? = null
    private var switchStopGeneration = 0

    fun snapshot(): PcRemoteSessionState = _state.value

    fun supports(type: String): Boolean = profile?.capabilities?.supports(type) == true

    fun supportsAll(vararg types: String): Boolean = types.all(::supports)

    suspend fun mouse(command: PcCommand, repeatable: Boolean = false): Boolean =
        repeatQueue.enqueue { mouseNow(command, repeatable) }

    suspend fun key(key: String): Boolean = repeatQueue.enqueue { keyNow(key) }

    suspend fun stopRepeat() {
        if (!reserveRepeatStop()) {
            repeatQueue.idle()
            return
        }
        repeatQueue.enqueue { completeRepeatStop() }
    }

    suspend fun toggleDrag(): Boolean {
        stopRepeat()
        val command = if (_state.value.dragging) PcCommands.dragEnd() else PcCommands.dragStart()
        if (!supports(command.type)) return false
        val ok = sender.send(command, PcResponseMode.Ack)
        if (ok) update { copy(dragging = !dragging) }
        return ok
    }

    suspend fun toggleModifier(key: String): Boolean = repeatQueue.enqueue { toggleModifierNow(key) }

    suspend fun command(command: PcCommand): Boolean {
        if (!supports(command.type)) return false
        stopRepeat()
        val ok = sender.send(command, responseModeFor(command.type))
        if (ok && _state.value.dragging && command.type in CLICK_TYPES) update { copy(dragging = false) }
        return ok
    }

    suspend fun openStream(): Boolean = streamQueue.enqueue { openStreamNow() }

    override suspend fun streamChunk(text: String): Boolean {
        if (text.isEmpty()) return false
        return streamQueue.enqueue {
            if (!supports(PcCommandTypes.KEYBOARD_STREAM_CHUNK) || !openStreamNow()) return@enqueue false
            val command = PcCommands.streamChunk(streamId ?: return@enqueue false, sequence, text)
            val ok = sendStreamCommand(command, responseModeFor(command.type))
            if (ok) sequence += 1
            ok
        }
    }

    override suspend fun streamKey(key: String): Boolean = streamQueue.enqueue {
        if (!supports(PcCommandTypes.KEYBOARD_STREAM_KEY) || !openStreamNow()) return@enqueue false
        val command = PcCommands.streamKey(streamId ?: return@enqueue false, sequence, key)
        val ok = sendStreamCommand(command, PcResponseMode.Ack)
        if (ok) sequence += 1
        ok
    }

    suspend fun closeStream() {
        streamQueue.enqueue {
            val id = streamId
            if (!_state.value.streamOpen || id == null) return@enqueue
            val command = PcCommands.streamClose(id, sequence)
            update { copy(streamOpen = false) }
            streamId = null
            sendStreamCommand(command, PcResponseMode.None)
        }
    }

    suspend fun shortcut(key: String): Boolean {
        if (!supports(PcCommandTypes.KEYBOARD_SHORTCUT)) return false
        val active = _state.value.modifiers.toList()
        if (!command(PcCommands.shortcut(active + key))) return false
        for (modifier in active) {
            val release = PcCommands.modifierUp(modifier)
            if (supports(release.type)) sender.send(release, responseModeFor(release.type))
        }
        if (active.isNotEmpty()) update { copy(modifiers = emptyList()) }
        return true
    }

    suspend fun cleanup() {
        stopRepeat()
        if (_state.value.dragging) sendQuietly(PcCommands.dragEnd())
        for (key in _state.value.modifiers) sendQuietly(PcCommands.modifierUp(key))
        closeStream()
        _state.value = PcRemoteSessionState()
    }

    fun dispose() {
        releaseSwitchStop()
    }

    private suspend fun mouseNow(command: PcCommand, repeatable: Boolean): Boolean {
        if (!supports(command.type)) return false
        if (_state.value.repeat != null) {
            if (reserveRepeatStop()) completeRepeatStop()
            return true
        }
        val repeatCommand = if (repeatable) repeatCommandFor(command) else null
        val mouseRepeat = profile?.capabilities?.mouseRepeat
        if (repeatCommand != null && repeatCommandsSupported() && mouseRepeat?.supported == true && mouseRepeat.enabled) {
            val ok = sender.send(PcCommands.repeatStart(repeatCommand), PcResponseMode.Ack)
            if (ok) {
                repeatingKey = null
                update { copy(repeat = command.type) }
                armSwitchStop()
            }
            return ok
        }
        return sender.send(command, responseModeFor(command.type))
    }

    private suspend fun keyNow(key: String): Boolean {
        if (!supports(PcCommandTypes.KEYBOARD_KEY)) return false
        val repeatable = repeatableKey(key)
        val current = _state.value
        if (current.repeat != null) {
            val ownRepeat = current.repeat == PcCommandTypes.KEYBOARD_KEY && repeatingKey == key
            if (reserveRepeatStop()) completeRepeatStop()
            if (ownRepeat) return true
            return sendKey(key)
        }
        if (repeatable) {
            val ok = sender.send(PcCommands.repeatStart(PcRepeatCommand.Key(key)), PcResponseMode.Ack)
            if (ok) {
                repeatingKey = key
                update { copy(repeat = PcCommandTypes.KEYBOARD_KEY) }
                armSwitchStop()
            }
            return ok
        }
        return sendKey(key)
    }

    private suspend fun sendKey(key: String): Boolean {
        val command = PcCommands.key(key)
        return sender.send(command, responseModeFor(command.type))
    }

    private suspend fun toggleModifierNow(key: String): Boolean {
        val active = key in _state.value.modifiers
        val command = if (active) PcCommands.modifierUp(key) else PcCommands.modifierDown(key)
        if (!supports(command.type)) return false
        if (reserveRepeatStop()) completeRepeatStop()
        val ok = sender.send(command, responseModeFor(command.type))
        if (ok) update { copy(modifiers = if (active) modifiers - key else modifiers + key) }
        return ok
    }

    private suspend fun openStreamNow(): Boolean {
        if (_state.value.streamOpen) return true
        if (!supports(PcCommandTypes.KEYBOARD_STREAM_OPEN) || !supports(PcCommandTypes.KEYBOARD_STREAM_CLOSE)) return false
        val id = streamIds.nextId()
        val ok = command(PcCommands.streamOpen(id))
        if (ok) {
            streamId = id
            sequence = 0
            update { copy(streamOpen = true) }
        }
        return ok
    }

    private suspend fun sendStreamCommand(command: PcCommand, responseMode: PcResponseMode): Boolean =
        repeatQueue.enqueue {
            if (reserveRepeatStop()) completeRepeatStop()
            sender.send(command, responseMode)
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
        releaseSwitchStop()
        update { copy(repeat = null) }
        return true
    }

    private suspend fun completeRepeatStop() {
        sendQuietly(PcCommands.repeatStop(), PcResponseMode.Ack)
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

    private suspend fun sendQuietly(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.None) {
        try {
            sender.send(command, responseMode)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
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
