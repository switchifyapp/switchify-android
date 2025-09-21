package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.service.techniques.radar.RadarSettings

@Composable
fun RadarSpeedStepper() {
    val context = LocalContext.current

    // Initialize RadarSettings if not already done
    RadarSettings.init(context)

    PreferenceValueSelector(
        value = RadarSettings.getSpeedLevel(),
        titleResId = R.string.preference_title_radar_speed,
        summaryResId = R.string.preference_summary_radar_speed,
        min = 1,
        max = 25,
        buttonLabelFormatter = { speedLevel -> speedLevel.toString() },
        displayFormatter = { speedLevel ->
            "Level $speedLevel (${RadarSettings.getSpeedLevelDescription(speedLevel)})"
        },
        onValueChanged = { newSpeedLevel ->
            RadarSettings.setSpeedLevel(newSpeedLevel, context)
        }
    )
} 