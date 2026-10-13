package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.BlockCountSection
import com.enaboapps.switchify.screens.settings.shared.BlockRadarSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.RadarSpeedStepper
import com.enaboapps.switchify.service.techniques.radar.RadarSettings

@Composable
fun RadarSettingsView() {
    val context = LocalContext.current
    RadarSettings.init(context)

    val radarModes = listOf(
        RadarSettings.Modes.MODE_SINGLE,
        RadarSettings.Modes.MODE_BLOCK
    )
    var currentMode by remember { mutableStateOf(RadarSettings.getMode()) }

    val radarPositionTop = stringResource(R.string.radar_position_top)
    val radarPositionBottom = stringResource(R.string.radar_position_bottom)
    val radarPositionCenter = stringResource(R.string.radar_position_center)
    val positionLabel = { index: Int ->
        when (index) {
            0 -> radarPositionTop
            1 -> radarPositionBottom
            2 -> radarPositionCenter
            else -> ""
        }
    }

    Section(titleResId = R.string.section_title_radar_mode) {
        Picker(
            titleResId = R.string.picker_title_select_radar_mode,
            selectedItem = currentMode,
            items = radarModes,
            onItemSelected = { mode ->
                RadarSettings.setMode(mode)
                currentMode = mode
            },
            itemToString = { RadarSettings.getModeName(it) },
            itemDescription = { RadarSettings.getModeDescription(it) }
        )
    }

    Section(titleResId = R.string.section_title_radar_speed) {
        RadarSpeedStepper()

        AnimatedVisibility(
            visible = currentMode == RadarSettings.Modes.MODE_BLOCK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BlockRadarSpeedStepper()
        }
    }

    AnimatedVisibility(
        visible = currentMode == RadarSettings.Modes.MODE_BLOCK,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        BlockCountSection(
            initialBlockCount = RadarSettings.getBlockCount(),
            onBlockCountChanged = { RadarSettings.setBlockCount(it) }
        )
    }

    Section(titleResId = R.string.section_title_radar_interaction) {
        PreferenceSwitch(
            titleResId = R.string.preference_title_radar_slow_down_then_select,
            summaryResId = R.string.preference_summary_radar_slow_down_then_select,
            checked = RadarSettings.isSlowDownThenSelectEnabled(),
            onCheckedChange = {
                RadarSettings.setSlowDownThenSelectEnabled(it)
            }
        )

        PreferenceValueSelector(
            value = when (RadarSettings.getStartingPosition()) {
                RadarSettings.StartingPosition.TOP -> 0
                RadarSettings.StartingPosition.BOTTOM -> 1
                RadarSettings.StartingPosition.CENTER -> 2
                else -> 0
            },
            titleResId = R.string.preference_title_radar_starting_position,
            summaryResId = R.string.preference_summary_radar_starting_position,
            values = intArrayOf(0, 1, 2),
            buttonLabelFormatter = positionLabel,
            displayFormatter = positionLabel,
            onValueChanged = { index ->
                val position = when (index) {
                    0 -> RadarSettings.StartingPosition.TOP
                    1 -> RadarSettings.StartingPosition.BOTTOM
                    2 -> RadarSettings.StartingPosition.CENTER
                    else -> RadarSettings.StartingPosition.TOP
                }
                RadarSettings.setStartingPosition(position)
            }
        )
    }
}
