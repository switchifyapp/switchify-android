package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PcKeyboardKey
import org.junit.Assert.assertEquals
import org.junit.Test

class PcTypingControlScreenTest {
    @Test
    fun editingKeysUseStableOrder() {
        val keys = pcEditingKeySpecs().map { it.key }

        assertEquals(
            listOf(PcKeyboardKey.Backspace, PcKeyboardKey.Delete, PcKeyboardKey.Enter),
            keys
        )
    }

    @Test
    fun spacingKeysUseStableOrder() {
        val keys = pcSpacingKeySpecs().map { it.key }

        assertEquals(
            listOf(PcKeyboardKey.Space, PcKeyboardKey.Tab, PcKeyboardKey.Escape),
            keys
        )
    }

    @Test
    fun cursorKeysUseStableOrder() {
        val keys = pcCursorKeySpecs().map { it.key }

        assertEquals(
            listOf(
                PcKeyboardKey.ArrowUp,
                PcKeyboardKey.ArrowLeft,
                PcKeyboardKey.ArrowRight,
                PcKeyboardKey.ArrowDown
            ),
            keys
        )
    }

    @Test
    fun documentKeysUseStableOrder() {
        val keys = pcDocumentKeySpecs().map { it.key }

        assertEquals(
            listOf(PcKeyboardKey.Home, PcKeyboardKey.End, PcKeyboardKey.PageUp, PcKeyboardKey.PageDown),
            keys
        )
    }
}
