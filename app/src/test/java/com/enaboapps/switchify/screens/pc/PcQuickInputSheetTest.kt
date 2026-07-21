package com.enaboapps.switchify.screens.pc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcQuickInputSheetTest {
    @Test
    fun quickInputButtonShownOnMouseSurface() {
        assertTrue(shouldShowPcQuickInputButton(PcControlSurface.Mouse))
    }

    @Test
    fun quickInputButtonShownOnWindowSurface() {
        assertTrue(shouldShowPcQuickInputButton(PcControlSurface.Window))
    }

    @Test
    fun quickInputButtonHiddenOnTypingSurface() {
        assertFalse(shouldShowPcQuickInputButton(PcControlSurface.Typing))
    }
}
