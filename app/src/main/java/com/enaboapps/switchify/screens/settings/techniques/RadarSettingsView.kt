package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.RadarSpeedStepper
import com.enaboapps.switchify.service.techniques.radar.RadarSettings

@Composable
fun RadarSettingsView() {
    val context = LocalContext.current

    // Initialize RadarSettings
    RadarSettings.init(context)

    Section(titleResId = R.string.section_title_radar_speed) {
        RadarSpeedStepper()
    }

    Section(titleResId = R.string.section_title_radar_interaction) {
        PreferenceSwitch(
            titleResId = R.string.preference_title_radar_slow_down_then_select,
            summaryResId = R.string.preference_summary_radar_slow_down_then_select,
            checked = RadarSettings.isSlowDownThenSelectEnabled(),
            onCheckedChange = {
                RadarSettings.setSlowDownThenSelectEnabled(it, context)
            }
        )

        PreferenceValueSelector(
            value = when (RadarSettings.getStartingPosition()) {
                RadarSettings.StartingPosition.TOP -> 0
                RadarSettings.StartingPosition.BOTTOM -> 1
                else -> 0
            },
            titleResId = R.string.preference_title_radar_starting_position,
            summaryResId = R.string.preference_summary_radar_starting_position,
            values = intArrayOf(0, 1),
            buttonLabelFormatter = { index ->
                when (index) {
                    0 -> context.getString(R.string.radar_position_top)
                    1 -> context.getString(R.string.radar_position_bottom)
                    else -> ""
                }
            },
            displayFormatter = { index ->
                when (index) {
                    0 -> context.getString(R.string.radar_position_top)
                    1 -> context.getString(R.string.radar_position_bottom)
                    else -> ""
                }
            },
            onValueChanged = { index ->
                val position = when (index) {
                    0 -> RadarSettings.StartingPosition.TOP
                    1 -> RadarSettings.StartingPosition.BOTTOM
                    else -> RadarSettings.StartingPosition.TOP
                }
                RadarSettings.setStartingPosition(position, context)
            }
        )
    }
}