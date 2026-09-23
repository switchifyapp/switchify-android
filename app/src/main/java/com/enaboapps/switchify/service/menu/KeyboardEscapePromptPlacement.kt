package com.enaboapps.switchify.service.menu

/**
 * Where the keyboard escape prompt's overlay window goes. Pure so it can be
 * unit tested without a display.
 */
internal sealed class KeyboardEscapePromptPlacement {
    /**
     * A band as wide as the keyboard whose bottom edge sits on the keyboard's
     * top edge, so the card docks just above the keys without covering them.
     */
    data class Docked(val x: Int, val y: Int, val width: Int, val height: Int) : KeyboardEscapePromptPlacement()

    /** Keyboard bounds are unknown; fall back to the centre of the display. */
    data object Centered : KeyboardEscapePromptPlacement()

    companion object {
        fun forKeyboard(
            keyboardLeft: Int?,
            keyboardTop: Int?,
            keyboardWidth: Int?,
            keyboardHeight: Int?,
            bandHeight: Int
        ): KeyboardEscapePromptPlacement {
            if (keyboardLeft == null || keyboardTop == null || keyboardWidth == null || keyboardHeight == null) {
                return Centered
            }
            if (keyboardWidth <= 0 || keyboardHeight <= 0 || bandHeight <= 0) return Centered
            val y = (keyboardTop - bandHeight).coerceAtLeast(0)
            return Docked(keyboardLeft, y, keyboardWidth, bandHeight)
        }
    }
}
