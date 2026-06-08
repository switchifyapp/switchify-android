package com.enaboapps.switchify.pc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcKeyboardTextTest {
    @Test
    fun acceptsCommittedTextPayloads() {
        assertTrue(isSafePcTypedText("Hello"))
        assertTrue(isSafePcTypedText("café"))
        assertTrue(isSafePcTypedText("👋"))
        assertTrue(isSafePcTypedText("line one\nline two"))
        assertTrue(isSafePcTypedText("tab\ttext"))
        assertTrue(isSafePcTypedText("carriage\rreturn"))
        assertTrue(isSafePcTypedText(""))
    }

    @Test
    fun rejectsOversizedTextPayload() {
        assertFalse(isSafePcTypedText("x".repeat(PC_KEYBOARD_TYPE_TEXT_MAX_LENGTH + 1)))
    }

    @Test
    fun rejectsUnsupportedControlCharacters() {
        assertFalse(isSafePcTypedText("hello\u0000world"))
        assertFalse(isSafePcTypedText("hello\u001Bworld"))
        assertFalse(isSafePcTypedText("hello\u007Fworld"))
        assertFalse(isSafePcTypedText("hello\u0085world"))
    }
}
