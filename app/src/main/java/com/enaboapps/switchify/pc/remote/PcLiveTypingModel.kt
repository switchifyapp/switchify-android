package com.enaboapps.switchify.pc.remote

import com.enaboapps.switchify.pc.remote.actions.PcActionRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PcLiveTypingFailure {
    Text,
    Enter
}

class PcLiveTypingModel(
    private val session: PcRemoteSession,
    private val scope: CoroutineScope
) {
    private val controller = PcLiveTypingController(session)
    private val _text = MutableStateFlow("")
    private val _failure = MutableStateFlow<PcLiveTypingFailure?>(null)
    private val _submitting = MutableStateFlow(false)
    private var revision = 0

    val text: StateFlow<String> = _text.asStateFlow()
    val failure: StateFlow<PcLiveTypingFailure?> = _failure.asStateFlow()
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    val supported: Boolean get() = session.supportsAll(*LIVE_COMMANDS)

    fun change(next: String) {
        if (_submitting.value) return
        if (isEnterPress(_text.value, next)) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { submit() }
            return
        }
        val limited = PcTextLimit.limit(next)
        _text.value = limited
        reconcile(limited)
    }

    fun retryText() = reconcile(_text.value)

    suspend fun submit() {
        if (!supported || _submitting.value) return
        _submitting.value = true
        val current = ++revision
        _failure.value = null
        try {
            val sent = controller.submitLine()
            if (current == revision) {
                if (sent) _text.value = "" else _failure.value = PcLiveTypingFailure.Enter
            }
        } finally {
            _submitting.value = false
        }
    }

    private fun reconcile(next: String) {
        val current = ++revision
        _failure.value = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val sent = controller.update(next)
            if (current == revision) _failure.value = if (sent) null else PcLiveTypingFailure.Text
        }
    }

    private fun isEnterPress(current: String, next: String): Boolean {
        if (next.length != current.length + 1) return false
        val index = next.indices.firstOrNull { it >= current.length || next[it] != current[it] } ?: return false
        return next[index] == '\n' && next.removeRange(index, index + 1) == current
    }

    private companion object {
        val LIVE_COMMANDS = PcActionRuntime.LIVE_TYPING_COMMANDS
    }
}
