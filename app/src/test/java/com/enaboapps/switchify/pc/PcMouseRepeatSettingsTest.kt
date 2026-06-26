package com.enaboapps.switchify.pc

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcMouseRepeatSettingsTest {
    @Test
    fun enabledReadsDefaultTrue() {
        val settings = settings()

        assertTrue(settings.isEnabled())
    }

    @Test
    fun disabledValueReadsFalse() {
        val settings = settings(booleans = mapOf(PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT to false))

        assertFalse(settings.isEnabled())
    }

    @Test
    fun defaultIntervalIs250() {
        val settings = settings()

        assertEquals(250L, settings.intervalMs())
    }

    @Test
    fun intervalBelowMinimumClampsTo100() {
        val settings = settings(longs = mapOf(PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT_INTERVAL to 50L))

        assertEquals(100L, settings.intervalMs())
    }

    @Test
    fun intervalAboveMaximumClampsTo2000() {
        val settings = settings(longs = mapOf(PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT_INTERVAL to 2500L))

        assertEquals(2000L, settings.intervalMs())
    }

    private fun settings(
        booleans: Map<String, Boolean> = emptyMap(),
        longs: Map<String, Long> = emptyMap()
    ): PreferencePcMouseRepeatSettings {
        return PreferencePcMouseRepeatSettings(
            getBooleanValue = { key, default -> booleans[key] ?: default },
            getLongValue = { key, default -> longs[key] ?: default }
        )
    }
}
