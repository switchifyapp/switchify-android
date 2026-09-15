package com.enaboapps.switchify.service.scanning.preferences

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPreferencePolicyTest {
    @Test
    fun coversEveryScanPreference() {
        val expected = setOf(
            PreferenceManager.PREFERENCE_KEY_APP_SCAN_TECHNIQUES,
            PreferenceManager.PREFERENCE_KEY_ACCESS_TECHNIQUE,
            PreferenceManager.PREFERENCE_KEY_SCAN_MODE,
            PreferenceManager.PREFERENCE_KEY_ROW_COLUMN_SCAN,
            PreferenceManager.PREFERENCE_KEY_GROUP_SCAN,
            PreferenceManager.PREFERENCE_KEY_SCAN_RATE,
            PreferenceManager.PREFERENCE_KEY_CURSOR_MODE,
            PreferenceManager.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
            PreferenceManager.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE,
            PreferenceManager.PREFERENCE_KEY_RADAR_STARTING_POSITION,
            PreferenceManager.PREFERENCE_KEY_SCAN_CYCLES,
            PreferenceManager.PREFERENCE_KEY_POINT_SCAN_LINE_SPEED_LEVEL,
            PreferenceManager.PREFERENCE_KEY_RADAR_SPEED_LEVEL,
            PreferenceManager.PREFERENCE_KEY_RADAR_SLOW_DOWN_THEN_SELECT,
            PreferenceManager.PREFERENCE_KEY_AUTOMATICALLY_START_SCAN_AFTER_SELECTION,
            PreferenceManager.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM,
            PreferenceManager.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM_DELAY,
            PreferenceManager.PREFERENCE_KEY_AUTO_SELECT,
            PreferenceManager.PREFERENCE_KEY_AUTO_SELECT_DELAY,
            PreferenceManager.PREFERENCE_KEY_DIRECTLY_SELECT_KEYBOARD_KEYS,
            PreferenceManager.PREFERENCE_KEY_ASSISTED_SELECTION,
            PreferenceManager.PREFERENCE_KEY_MOVE_REPEAT,
            PreferenceManager.PREFERENCE_KEY_MOVE_REPEAT_DELAY,
            PreferenceManager.PREFERENCE_KEY_ITEM_SCAN_SPEECH,
            PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_TYPE,
            PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_MOVEMENT,
            PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_COUNTDOWN,
            PreferenceManager.PREFERENCE_KEY_SCAN_COLOR_SET
        )

        assertEquals(expected, ScanPreferencePolicy.supportedKeys)
        assertTrue(expected.all { ScanPreferencePolicy.effectFor(it) != null })
        assertNull(ScanPreferencePolicy.effectFor("unrelated"))
    }

    @Test
    fun visualChangesDoNotResetScanningOrTiming() {
        val plan = ScanPreferencePolicy.reduce(setOf(
            PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_TYPE,
            PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_MOVEMENT,
            PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_COUNTDOWN))
        assertEquals(setOf(ScanPreferenceEffect.REFRESH_HIGHLIGHT), plan.effects)
    }

    @Test
    fun structuralChangesDominateTimingChanges() {
        val plan = ScanPreferencePolicy.reduce(
            setOf(
                PreferenceManager.PREFERENCE_KEY_GROUP_SCAN,
                PreferenceManager.PREFERENCE_KEY_SCAN_RATE,
                PreferenceManager.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
                PreferenceManager.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE
            )
        )

        assertTrue(plan.contains(ScanPreferenceEffect.REFRESH_ITEM_STRUCTURE))
        assertFalse(plan.contains(ScanPreferenceEffect.REFRESH_ITEM_TIMING))
        assertTrue(plan.contains(ScanPreferenceEffect.REFRESH_POINT_STRUCTURE))
        assertFalse(plan.contains(ScanPreferenceEffect.REFRESH_POINT_TIMING))
    }

    @Test
    fun scanModeResetDominatesEveryTechniqueUpdate() {
        val plan = ScanPreferencePolicy.reduce(
            setOf(
                PreferenceManager.PREFERENCE_KEY_SCAN_MODE,
                PreferenceManager.PREFERENCE_KEY_ROW_COLUMN_SCAN,
                PreferenceManager.PREFERENCE_KEY_CURSOR_MODE,
                PreferenceManager.PREFERENCE_KEY_RADAR_STARTING_POSITION
            )
        )

        assertTrue(plan.contains(ScanPreferenceEffect.RESET_SCAN_MODE))
        assertFalse(plan.contains(ScanPreferenceEffect.REFRESH_ITEM_STRUCTURE))
        assertFalse(plan.contains(ScanPreferenceEffect.REFRESH_POINT_STRUCTURE))
        assertFalse(plan.contains(ScanPreferenceEffect.RESET_RADAR_ORIGIN))
    }
}
