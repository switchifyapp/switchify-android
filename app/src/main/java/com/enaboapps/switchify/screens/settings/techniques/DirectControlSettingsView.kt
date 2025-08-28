package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.techniques.directcontrol.DirectControlSettings

@Composable
fun DirectControlSettingsView() {
    val context = LocalContext.current
    val prefs = PreferenceManager(context)
    val settings = DirectControlSettings(context)

    var speedLevel by remember { mutableIntStateOf(settings.speedLevel()) }
    var precisionEnabled by remember { mutableStateOf(settings.precisionEnabled()) }
    // precision multiplier fixed internally; no UI

    Section(titleResId = R.string.section_title_direct_control_movement) {
        PreferenceTimeStepper(
            value = speedLevel.toLong(),
            titleResId = R.string.preference_title_direct_control_speed,
            summaryResId = R.string.preference_summary_direct_control_speed,
            min = 1,
            max = 5,
            step = 1,
            onValueChanged = { v ->
                speedLevel = v.toInt()
                prefs.setIntegerValue(DirectControlSettings.KEY_SPEED_LEVEL, speedLevel)
            }
        )
    }

    Section(titleResId = R.string.section_title_direct_control_precision) {
        PreferenceSwitch(
            titleResId = R.string.preference_title_direct_control_precision_enabled,
            summaryResId = R.string.preference_summary_direct_control_precision_enabled,
            checked = precisionEnabled,
            onCheckedChange = {
                precisionEnabled = it
                prefs.setBooleanValue(DirectControlSettings.KEY_PRECISION_ENABLED, it)
            }
        )

    }
}
