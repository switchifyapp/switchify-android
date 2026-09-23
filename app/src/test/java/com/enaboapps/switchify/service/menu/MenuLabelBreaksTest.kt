package com.enaboapps.switchify.service.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuLabelBreaksTest {
    @Test fun breakInsideAWordIsMidWord() {
        assertTrue(MenuLabelBreaks.isMidWordBreak("Accessibility", 7))
        assertTrue(MenuLabelBreaks.isMidWordBreak("Tap and Hold", 6))
        assertTrue(MenuLabelBreaks.isMidWordBreak("Page 12", 6))
    }

    @Test fun breakAtWhitespaceIsNotMidWord() {
        // StaticLayout keeps trailing whitespace on the preceding line.
        assertFalse(MenuLabelBreaks.isMidWordBreak("Tap and Hold", 4))
        assertFalse(MenuLabelBreaks.isMidWordBreak("Tap and Hold", 3))
    }

    @Test fun breakAtPunctuationIsNotMidWord() {
        assertFalse(MenuLabelBreaks.isMidWordBreak("Wi-Fi", 3))
        assertFalse(MenuLabelBreaks.isMidWordBreak("Wi-Fi", 2))
        assertFalse(MenuLabelBreaks.isMidWordBreak("Volume/Media", 7))
    }

    @Test fun textEdgesAreNeverMidWord() {
        assertFalse(MenuLabelBreaks.isMidWordBreak("Settings", 0))
        assertFalse(MenuLabelBreaks.isMidWordBreak("Settings", 8))
        assertFalse(MenuLabelBreaks.isMidWordBreak("", 0))
    }

    @Test fun scriptsThatBreakBetweenCharactersAreNotMidWord() {
        assertFalse(MenuLabelBreaks.isMidWordBreak("設定項目", 2))
        assertFalse(MenuLabelBreaks.isMidWordBreak("ひらがな", 2))
        assertFalse(MenuLabelBreaks.isMidWordBreak("การตั้ง", 3))
    }
}
