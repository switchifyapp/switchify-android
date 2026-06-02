package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.service.techniques.radar.RadarSettings
import com.enaboapps.switchify.service.utils.ContinuousLineSpeedUtils

@Composable
fun RadarSpeedStepper() {
    val context = LocalContext.current

    // Initialize RadarSettings if not already done
    RadarSettings.init(context)

    PreferenceValueSelector(
        value = RadarSettings.getSpeedLevel(),
        titleResId = R.string.preference_title_radar_speed,
        summaryResId = R.string.preference_summary_radar_speed,
        values = ContinuousLineSpeedUtils.getPresetOptions()
            .map { it.representativeLevel }
            .toIntArray(),
        buttonLabelFormatter = { speedLevel -> RadarSettings.getSpeedLevelDescription(speedLevel) },
        displayFormatter = { speedLevel -> RadarSettings.getSpeedLevelDescription(speedLevel) },
        onValueChanged = { newSpeedLevel ->
            RadarSettings.setSpeedLevel(newSpeedLevel, context)
        }
    )
} 
