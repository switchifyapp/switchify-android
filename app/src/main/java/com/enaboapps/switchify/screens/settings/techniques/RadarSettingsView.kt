package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section

@Composable
fun RadarSettingsView() {
    val preferenceManager = PreferenceManager(LocalContext.current)

    Section(titleResId = R.string.section_title_radar_speed) {
        PreferenceTimeStepper(
            value = preferenceManager.getLongValue(
                PreferenceManager.PREFERENCE_KEY_RADAR_SCAN_RATE,
                1000
            ),
            titleResId = R.string.preference_title_radar_speed,
            summaryResId = R.string.preference_summary_radar_speed,
            min = 10,
            max = 5000,
            step = 10,
            onValueChanged = { newValue ->
                preferenceManager.setLongValue(
                    PreferenceManager.PREFERENCE_KEY_RADAR_SCAN_RATE,
                    newValue
                )
            }
        )
    }
}