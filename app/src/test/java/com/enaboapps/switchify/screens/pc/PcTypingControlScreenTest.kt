package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PcKeyboardKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun cursorKeysUseSingleRowOrder() {
        val keys = pcCursorKeySpecs().map { it.key }

        assertEquals(
            listOf(
                PcKeyboardKey.ArrowLeft,
                PcKeyboardKey.ArrowUp,
                PcKeyboardKey.ArrowDown,
                PcKeyboardKey.ArrowRight
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

    @Test
    fun functionKeysUseStableOrder() {
        val keys = pcFunctionKeySpecs().map { it.key }

        assertEquals(
            listOf(
                PcKeyboardKey.F1,
                PcKeyboardKey.F2,
                PcKeyboardKey.F3,
                PcKeyboardKey.F4,
                PcKeyboardKey.F5,
                PcKeyboardKey.F6,
                PcKeyboardKey.F7,
                PcKeyboardKey.F8,
                PcKeyboardKey.F9,
                PcKeyboardKey.F10,
                PcKeyboardKey.F11,
                PcKeyboardKey.F12
            ),
            keys
        )
    }

    @Test
    fun compactCommandsUseStableScanOrder() {
        val specs = pcTypingCompactCommandSpecs()
        val keys = specs.mapNotNull { (it as? PcTypingCompactCommandSpec.Key)?.spec?.key }

        assertTrue(specs[0] is PcTypingCompactCommandSpec.Send)
        assertTrue(specs[1] is PcTypingCompactCommandSpec.Clear)
        assertEquals(
            listOf(
                PcKeyboardKey.Backspace,
                PcKeyboardKey.Delete,
                PcKeyboardKey.Enter,
                PcKeyboardKey.Space,
                PcKeyboardKey.Tab,
                PcKeyboardKey.Escape,
                PcKeyboardKey.ArrowLeft,
                PcKeyboardKey.ArrowUp,
                PcKeyboardKey.ArrowDown,
                PcKeyboardKey.ArrowRight,
                PcKeyboardKey.Home,
                PcKeyboardKey.End,
                PcKeyboardKey.PageUp,
                PcKeyboardKey.PageDown,
                PcKeyboardKey.F1,
                PcKeyboardKey.F2,
                PcKeyboardKey.F3,
                PcKeyboardKey.F4,
                PcKeyboardKey.F5,
                PcKeyboardKey.F6,
                PcKeyboardKey.F7,
                PcKeyboardKey.F8,
                PcKeyboardKey.F9,
                PcKeyboardKey.F10,
                PcKeyboardKey.F11,
                PcKeyboardKey.F12
            ),
            keys
        )
    }
}
