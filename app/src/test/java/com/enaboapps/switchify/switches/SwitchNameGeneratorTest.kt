package com.enaboapps.switchify.switches

import org.junit.Assert.assertEquals
import org.junit.Test

class SwitchNameGeneratorTest {

    @Test
    fun stripsPrefixAndTitleCasesWords() {
        assertEquals("Button A", SwitchNameGenerator.fromKeyCodeString("KEYCODE_BUTTON_A", 96))
        assertEquals("Media Next", SwitchNameGenerator.fromKeyCodeString("KEYCODE_MEDIA_NEXT", 87))
        assertEquals("Dpad Up", SwitchNameGenerator.fromKeyCodeString("KEYCODE_DPAD_UP", 19))
    }

    @Test
    fun keepsShortTokensUppercase() {
        assertEquals("F1", SwitchNameGenerator.fromKeyCodeString("KEYCODE_F1", 131))
        assertEquals("A", SwitchNameGenerator.fromKeyCodeString("KEYCODE_A", 29))
        assertEquals("Numpad 5", SwitchNameGenerator.fromKeyCodeString("KEYCODE_NUMPAD_5", 149))
    }

    @Test
    fun fallsBackToKeyNumberForUnknownCodes() {
        assertEquals("Key 999", SwitchNameGenerator.fromKeyCodeString("999", 999))
        assertEquals("Key 999", SwitchNameGenerator.fromKeyCodeString(null, 999))
        assertEquals("Key 999", SwitchNameGenerator.fromKeyCodeString("", 999))
    }
}
