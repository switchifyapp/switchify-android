package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.RadarSpeedStepper
import com.enaboapps.switchify.service.scanning.ScanSettings

@Composable
fun RadarSettingsView() {
    val context = LocalContext.current
    val preferenceManager = PreferenceManager(context)

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

        PreferenceValueSelector(
            value = when (preferenceManager.getStringValue(
                PreferenceManager.Keys.PREFERENCE_KEY_RADAR_STARTING_POSITION,
                ScanSettings.RADAR_START_TOP
            )) {
                ScanSettings.RADAR_START_TOP -> 0
                ScanSettings.RADAR_START_BOTTOM -> 1
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
                    0 -> ScanSettings.RADAR_START_TOP
                    1 -> ScanSettings.RADAR_START_BOTTOM
                    else -> ScanSettings.RADAR_START_TOP
                }
                preferenceManager.setStringValue(
                    PreferenceManager.Keys.PREFERENCE_KEY_RADAR_STARTING_POSITION,
                    position
                )
            }
        )
    }
}