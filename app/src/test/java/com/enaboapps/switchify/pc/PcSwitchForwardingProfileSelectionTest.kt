package com.enaboapps.switchify.pc

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcSwitchForwardingProfileSelectionTest {
    private val profiles = listOf(
        PcSwitchProfileSummary("builtin.grid3", 1, "Grid 3", "grid3", emptyList()),
        PcSwitchProfileSummary("builtin.keyboard", 1, "Generic keyboard", "mapped", emptyList()),
        PcSwitchProfileSummary("custom", 3, "Writing", "mapped", emptyList())
    )

    @Test
    fun persistenceUsesForwardingNamespace() {
        assertEquals(
            "pc_switch_forwarding_profiles",
            PcSwitchForwardingProfileStore.PREFERENCES_NAME
        )
        assertEquals(
            "pc_switch_forwarding_hold_to_stop_duration",
            PreferenceManager.PREFERENCE_KEY_PC_SWITCH_FORWARDING_HOLD_TO_STOP_DURATION
        )
    }

    @Test
    fun restoresRememberedProfileForPc() {
        val selection = selectPcSwitchForwardingProfile(profiles, "custom")

        assertEquals("custom", selection.profile?.id)
        assertFalse(selection.rememberedProfileUnavailable)
        assertEquals(null, selection.fallbackProfileName)
    }

    @Test
    fun missingRememberedProfileFallsBackToGenericKeyboard() {
        val selection = selectPcSwitchForwardingProfile(profiles, "removed")

        assertEquals("builtin.keyboard", selection.profile?.id)
        assertTrue(selection.rememberedProfileUnavailable)
        assertEquals("Generic keyboard", selection.fallbackProfileName)
    }

    @Test
    fun missingRememberedProfileReportsActualFirstProfileFallback() {
        val selection = selectPcSwitchForwardingProfile(listOf(profiles.last()), "removed")

        assertEquals("custom", selection.profile?.id)
        assertEquals("Writing", selection.fallbackProfileName)
    }

    @Test
    fun emptyCatalogHasNoFallbackNotice() {
        val selection = selectPcSwitchForwardingProfile(emptyList(), "removed")

        assertEquals(null, selection.profile)
        assertTrue(selection.rememberedProfileUnavailable)
        assertEquals(null, selection.fallbackProfileName)
    }
}
