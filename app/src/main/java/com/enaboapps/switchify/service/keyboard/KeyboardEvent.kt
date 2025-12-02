package com.enaboapps.switchify.service.keyboard

/**
 * Represents events that trigger keyboard state transitions.
 *
 * These events are processed by KeyboardStateMachine to determine
 * valid state transitions and prevent invalid state changes.
 */
sealed class KeyboardEvent {
    /**
     * Keyboard became visible to the user.
     */
    data object KeyboardShown : KeyboardEvent()

    /**
     * Keyboard was hidden/dismissed.
     */
    data object KeyboardHidden : KeyboardEvent()

    /**
     * User triggered escape from keyboard (cycle break selected).
     */
    data object EscapeRequested : KeyboardEvent()

    /**
     * User selected "Scan Keyboard" from menu to return to scanning.
     */
    data object ReturnRequested : KeyboardEvent()

    /**
     * Return transition completed after stabilization delay.
     */
    data object ReturnCompleted : KeyboardEvent()
}
