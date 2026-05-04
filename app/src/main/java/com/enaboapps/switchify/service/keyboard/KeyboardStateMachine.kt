package com.enaboapps.switchify.service.keyboard

import android.util.Log

class KeyboardStateMachine {
    private var currentState: KeyboardScanState = KeyboardScanState.HIDDEN

    companion object {
        private const val TAG = "KeyboardStateMachine"
    }

    fun transition(event: KeyboardEvent): KeyboardScanState? {
        val newState = when (currentState) {
            KeyboardScanState.HIDDEN -> when (event) {
                is KeyboardEvent.KeyboardShown -> KeyboardScanState.SCANNING
                else -> null
            }

            KeyboardScanState.SCANNING -> when (event) {
                is KeyboardEvent.KeyboardHidden -> KeyboardScanState.HIDDEN
                is KeyboardEvent.EscapeRequested -> KeyboardScanState.ESCAPED
                else -> null
            }

            KeyboardScanState.ESCAPED -> when (event) {
                is KeyboardEvent.KeyboardHidden -> KeyboardScanState.HIDDEN
                is KeyboardEvent.ReturnRequested -> KeyboardScanState.RETURNING
                else -> null
            }

            KeyboardScanState.RETURNING -> when (event) {
                is KeyboardEvent.KeyboardHidden -> KeyboardScanState.HIDDEN
                is KeyboardEvent.ReturnCompleted -> KeyboardScanState.SCANNING
                else -> null
            }
        }

        return newState?.also {
            logd("State transition: $currentState -> $it (event: ${event::class.simpleName})")
            currentState = it
        } ?: run {
            logw("Invalid transition: ${event::class.simpleName} in state $currentState")
            null
        }
    }

    fun getCurrentState(): KeyboardScanState = currentState

    fun isInState(state: KeyboardScanState): Boolean = currentState == state

    fun canTransition(event: KeyboardEvent): Boolean {
        return when (currentState) {
            KeyboardScanState.HIDDEN -> event is KeyboardEvent.KeyboardShown
            KeyboardScanState.SCANNING -> event is KeyboardEvent.KeyboardHidden || event is KeyboardEvent.EscapeRequested
            KeyboardScanState.ESCAPED -> event is KeyboardEvent.KeyboardHidden || event is KeyboardEvent.ReturnRequested
            KeyboardScanState.RETURNING -> event is KeyboardEvent.KeyboardHidden || event is KeyboardEvent.ReturnCompleted
        }
    }

    fun reset() {
        logd("State machine reset to HIDDEN")
        currentState = KeyboardScanState.HIDDEN
    }

    private fun logd(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logw(message: String) {
        runCatching { Log.w(TAG, message) }
    }
}
