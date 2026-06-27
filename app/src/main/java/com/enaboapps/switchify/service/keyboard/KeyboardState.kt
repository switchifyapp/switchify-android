package com.enaboapps.switchify.service.keyboard

import android.graphics.Rect

/**
 * Represents the current state of keyboard scanning.
 *
 * This data class provides both raw state properties and computed properties
 * for UI decision logic. UI components can observe this state reactively.
 *
 * @property isVisible Whether the keyboard is currently visible
 * @property isEscaped Whether the user has escaped from keyboard scanning
 * @property isDirectSelectEnabled Whether direct selection of keyboard keys is enabled in settings
 * @property keyboardBounds Bounds of the IME window in screen coordinates, or null if unknown.
 *           Treated as a snapshot — callers must not mutate.
 */
data class KeyboardState(
    val isVisible: Boolean = false,
    val isEscaped: Boolean = false,
    val isDirectSelectEnabled: Boolean = false,
    val keyboardBounds: Rect? = null,
    val keyboardWindowTarget: KeyboardWindowTarget? = null
) {
    /**
     * Whether the keyboard escape prompt should be shown.
     * Show when actively scanning keyboard (user can escape).
     */
    val shouldShowEscapePrompt: Boolean
        get() = isVisible && !isEscaped

    /**
     * Whether "Scan Keyboard" menu item should be shown.
     * Show when user has escaped from keyboard and can return to it.
     */
    val shouldShowScanKeyboardMenuItem: Boolean
        get() = isVisible && isEscaped
}
