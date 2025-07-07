package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.RadarSpeedStepper

@Composable
fun RadarSettingsView() {
    val preferenceManager = PreferenceManager(LocalContext.current)
    
    Section(titleResId = R.string.section_title_radar_speed) {
        RadarSpeedStepper()
    }
    
    Section(titleResId = R.string.section_title_radar_interaction) {
        PreferenceSwitch(
            titleResId = R.string.preference_title_radar_slow_down_then_select,
            summaryResId = R.string.preference_summary_radar_slow_down_then_select,
            checked = preferenceManager.getBooleanValue(
                PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SLOW_DOWN_THEN_SELECT,
                false
            ),
            onCheckedChange = {
                preferenceManager.setBooleanValue(
                    PreferenceManager.Keys.PREFERENCE_KEY_RADAR_SLOW_DOWN_THEN_SELECT,
                    it
                )
            }
        )
    }
}