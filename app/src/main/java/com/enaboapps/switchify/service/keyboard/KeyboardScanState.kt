package com.enaboapps.switchify.service.keyboard

/**
 * Represents the possible states of keyboard scanning.
 *
 * This enum defines an explicit state machine for keyboard interactions,
 * making state transitions clear and handling edge cases properly.
 */
enum class KeyboardScanState {
    /**
     * Keyboard is not visible.
     * Initial state and state after keyboard is dismissed.
     */
    HIDDEN,

    /**
     * Keyboard is visible and actively being scanned.
     * User can interact with keyboard keys through scanning.
     */
    SCANNING,

    /**
     * User has escaped from keyboard scanning.
     * Keyboard is still visible but user is in the menu.
     */
    ESCAPED,

    /**
     * Transitioning back to scanning after escape.
     * This intermediate state handles the delay needed for keyboard stabilization.
     */
    RETURNING
}
