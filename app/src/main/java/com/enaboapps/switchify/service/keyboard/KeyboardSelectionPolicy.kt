package com.enaboapps.switchify.service.keyboard

/**
 * Encapsulates all keyboard-related business rules and decision logic.
 *
 * This class extracts scattered keyboard decision logic into a single, testable location.
 * All keyboard UI visibility and behavior rules are centralized here.
 */
class KeyboardSelectionPolicy {
    /**
     * Determines if auto-select should be bypassed for direct keyboard selection.
     *
     * Bypass when:
     * - Keyboard is visible
     * - Direct selection is enabled in settings
     * - User has not escaped from keyboard
     *
     * When bypassed, selected keys are immediately performed without showing menu.
     */
    fun shouldBypassAutoSelect(state: KeyboardState): Boolean {
        return state.isVisible &&
                state.isDirectSelectEnabled &&
                !state.isEscaped
    }

    /**
     * Determines if "Scan Keyboard" menu item should be shown.
     *
     * Show when user has escaped from keyboard and can return to it.
     */
    fun shouldShowScanKeyboardMenuItem(state: KeyboardState): Boolean {
        return state.isVisible && state.isEscaped
    }

    /**
     * Determines if keyboard escape prompt should be shown.
     *
     * Show when actively scanning keyboard (user can escape).
     */
    fun shouldShowEscapePrompt(state: KeyboardState): Boolean {
        return state.isVisible && !state.isEscaped
    }

    /**
     * Determines if cycle break should be enabled.
     *
     * Enable when actively scanning keyboard so user can escape.
     */
    fun shouldEnableCycleBreak(state: KeyboardState): Boolean {
        return state.isVisible && !state.isEscaped
    }
}
