package com.enaboapps.switchify.pc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun streamItemsSplitAsciiTextIntoBoundedChunks() {
        assertEquals(
            listOf(
                PcTextStreamItem.Chunk("Hello")
            ),
            pcTextStreamItemsFor("Hello")
        )
    }

    @Test
    fun streamItemsSplitLongTextIntoBoundedChunks() {
        val firstChunk = "a".repeat(PC_TEXT_STREAM_CHUNK_MAX_CHARS)

        assertEquals(
            listOf(
                PcTextStreamItem.Chunk(firstChunk),
                PcTextStreamItem.Chunk("b")
            ),
            pcTextStreamItemsFor(firstChunk + "b")
        )
    }

    @Test
    fun streamItemsKeepEmojiSurrogatePairTogether() {
        val wavingHand = "\uD83D\uDC4B"
        val prefix = "a".repeat(PC_TEXT_STREAM_CHUNK_MAX_CHARS - 1)

        assertEquals(
            listOf(
                PcTextStreamItem.Chunk(prefix),
                PcTextStreamItem.Chunk(wavingHand)
            ),
            pcTextStreamItemsFor(prefix + wavingHand)
        )
    }

    @Test
    fun streamItemsMapNewlinesToEnter() {
        assertEquals(
            listOf(
                PcTextStreamItem.Chunk("a"),
                PcTextStreamItem.Key(PcKeyboardKey.Enter),
                PcTextStreamItem.Chunk("b"),
                PcTextStreamItem.Key(PcKeyboardKey.Enter),
                PcTextStreamItem.Chunk("c")
            ),
            pcTextStreamItemsFor("a\nb\rc")
        )
    }

    @Test
    fun streamItemsMapTabToTabKey() {
        assertEquals(
            listOf(
                PcTextStreamItem.Chunk("a"),
                PcTextStreamItem.Key(PcKeyboardKey.Tab),
                PcTextStreamItem.Chunk("b")
            ),
            pcTextStreamItemsFor("a\tb")
        )
    }
}
