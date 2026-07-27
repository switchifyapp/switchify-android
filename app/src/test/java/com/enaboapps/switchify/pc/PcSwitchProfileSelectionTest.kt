package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcSwitchProfileSelectionTest {
    private val profiles = listOf(
        PcSwitchProfileSummary("builtin.grid3", 1, "Grid 3", "grid3", emptyList()),
        PcSwitchProfileSummary("builtin.keyboard", 1, "Generic keyboard", "mapped", emptyList()),
        PcSwitchProfileSummary("custom", 3, "Writing", "mapped", emptyList())
    )

    @Test
    fun restoresRememberedProfileForPc() {
        val selection = selectPcSwitchProfile(profiles, "custom")

        assertEquals("custom", selection.profile?.id)
        assertFalse(selection.rememberedProfileUnavailable)
    }

    @Test
    fun missingRememberedProfileFallsBackToGenericKeyboard() {
        val selection = selectPcSwitchProfile(profiles, "removed")

        assertEquals("builtin.keyboard", selection.profile?.id)
        assertTrue(selection.rememberedProfileUnavailable)
    }
}
