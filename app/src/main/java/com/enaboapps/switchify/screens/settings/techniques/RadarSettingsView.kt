package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.runtime.Composable
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.RadarSpeedStepper

@Composable
fun RadarSettingsView() {
    Section(titleResId = R.string.section_title_radar_speed) {
        RadarSpeedStepper()
    }
}