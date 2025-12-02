package com.enaboapps.switchify.service.keyboard

import android.util.Log

/**
 * State machine for keyboard scanning state transitions.
 *
 * This class implements an explicit state machine that:
 * - Defines all valid state transitions
 * - Prevents invalid state changes
 * - Logs all transitions for debugging
 * - Handles edge cases (e.g., keyboard closing during escape)
 *
 * State Transition Diagram:
 * ```
 *                    KeyboardShown
 *        HIDDEN ──────────────────→ SCANNING
 *           ↑                          │    ↑
 *           │                          │    │
 *           │ KeyboardHidden   EscapeRequested ReturnCompleted
 *           │                          │    │
 *           │                          ↓    │
 *           └────────────────────── ESCAPED │
 *                KeyboardHidden         │    │
 *                                       │    │
 *                              ReturnRequested
 *                                       │    │
 *                                       ↓    │
 *                                   RETURNING┘
 *                                 KeyboardHidden
 *                                       │
 *                                       ↓
 *                                    HIDDEN
 * ```
 */
class KeyboardStateMachine {
    private var currentState: KeyboardScanState = KeyboardScanState.HIDDEN

    companion object {
        private const val TAG = "KeyboardStateMachine"
    }

    /**
     * Processes an event and transitions to new state if valid.
     *
     * @param event The event triggering the transition
     * @return The new state if transition is valid, null otherwise
     */
    fun transition(event: KeyboardEvent): KeyboardScanState? {
        val newState = when (currentState) {
            KeyboardScanState.HIDDEN -> when (event) {
                is KeyboardEvent.KeyboardShown -> KeyboardScanState.SCANNING
                else -> null  // Invalid transition
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
            Log.d(TAG, "State transition: $currentState → $it (event: ${event::class.simpleName})")
            currentState = it
        } ?: run {
            Log.w(TAG, "Invalid transition: ${event::class.simpleName} in state $currentState")
            null
        }
    }

    /**
     * Gets the current state.
     */
    fun getCurrentState(): KeyboardScanState = currentState

    /**
     * Checks if currently in the specified state.
     */
    fun isInState(state: KeyboardScanState): Boolean = currentState == state

    /**
     * Checks if the given event would result in a valid transition.
     */
    fun canTransition(event: KeyboardEvent): Boolean {
        return when (currentState) {
            KeyboardScanState.HIDDEN -> event is KeyboardEvent.KeyboardShown
            KeyboardScanState.SCANNING -> event is KeyboardEvent.KeyboardHidden || event is KeyboardEvent.EscapeRequested
            KeyboardScanState.ESCAPED -> event is KeyboardEvent.KeyboardHidden || event is KeyboardEvent.ReturnRequested
            KeyboardScanState.RETURNING -> event is KeyboardEvent.KeyboardHidden || event is KeyboardEvent.ReturnCompleted
        }
    }

    /**
     * Resets the state machine to HIDDEN.
     * Used for testing or cleanup scenarios.
     */
    fun reset() {
        Log.d(TAG, "State machine reset to HIDDEN")
        currentState = KeyboardScanState.HIDDEN
    }
}
