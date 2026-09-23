package com.enaboapps.switchify.service.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardEscapePromptPlacementTest {
    @Test
    fun docksABandDirectlyAboveTheKeyboard() {
        val placement = KeyboardEscapePromptPlacement.forKeyboard(
            keyboardLeft = 0, keyboardTop = 1400, keyboardWidth = 1080, keyboardHeight = 900, bandHeight = 300
        )
        assertEquals(KeyboardEscapePromptPlacement.Docked(0, 1100, 1080, 300), placement)
    }

    @Test
    fun clampsToTheTopOfTheDisplayWhenTheKeyboardIsTall() {
        val placement = KeyboardEscapePromptPlacement.forKeyboard(
            keyboardLeft = 0, keyboardTop = 120, keyboardWidth = 1080, keyboardHeight = 2000, bandHeight = 300
        )
        assertEquals(KeyboardEscapePromptPlacement.Docked(0, 0, 1080, 300), placement)
    }

    @Test
    fun followsAFloatingKeyboardsHorizontalPosition() {
        val placement = KeyboardEscapePromptPlacement.forKeyboard(
            keyboardLeft = 200, keyboardTop = 900, keyboardWidth = 700, keyboardHeight = 500, bandHeight = 250
        )
        assertEquals(KeyboardEscapePromptPlacement.Docked(200, 650, 700, 250), placement)
    }

    @Test
    fun centresWhenBoundsAreMissingOrEmpty() {
        assertEquals(
            KeyboardEscapePromptPlacement.Centered,
            KeyboardEscapePromptPlacement.forKeyboard(null, null, null, null, 300)
        )
        assertEquals(
            KeyboardEscapePromptPlacement.Centered,
            KeyboardEscapePromptPlacement.forKeyboard(0, 1400, 0, 900, 300)
        )
        assertEquals(
            KeyboardEscapePromptPlacement.Centered,
            KeyboardEscapePromptPlacement.forKeyboard(0, 1400, 1080, 0, 300)
        )
    }
}
