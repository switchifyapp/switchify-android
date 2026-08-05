package com.enaboapps.switchify.service.scanning

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanModeTest {
    @Test
    fun exposesAutoAndManualModes() {
        assertEquals(
            listOf(ScanMode.Modes.MODE_AUTO, ScanMode.Modes.MODE_MANUAL),
            ScanMode.modes.map { it.id }
        )
    }

    @Test
    fun unknownModeDefaultsToAuto() {
        assertEquals(ScanMode.Modes.MODE_AUTO, ScanMode.fromId("unknown").id)
    }
}
