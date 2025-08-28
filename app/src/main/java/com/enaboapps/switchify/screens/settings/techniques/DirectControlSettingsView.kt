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
import com.enaboapps.switchify.components.PreferenceValueSelector
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
    var repeatDelay by remember { mutableIntStateOf(settings.repeatDelay().toInt()) }
    
    // Store context strings to avoid @Composable calls in lambdas
    val step1 = context.getString(R.string.direct_control_step_1)
    val step2 = context.getString(R.string.direct_control_step_2)
    val step3 = context.getString(R.string.direct_control_step_3)
    val step4 = context.getString(R.string.direct_control_step_4)
    val step5 = context.getString(R.string.direct_control_step_5)

    Section(titleResId = R.string.section_title_direct_control_movement) {
        PreferenceValueSelector(
            value = speedLevel,
            titleResId = R.string.preference_title_direct_control_speed,
            summaryResId = R.string.preference_summary_direct_control_speed,
            min = 1,
            max = 5,
            buttonLabelFormatter = {
                when (it) {
                    1 -> step1
                    2 -> step2
                    3 -> step3
                    4 -> step4
                    else -> step5
                }
            },
            displayFormatter = {
                when (it) {
                    1 -> step1
                    2 -> step2
                    3 -> step3
                    4 -> step4
                    else -> step5
                }
            },
            onValueChanged = { v ->
                speedLevel = v
                prefs.setIntegerValue(DirectControlSettings.KEY_SPEED_LEVEL, speedLevel)
            }
        )
        PreferenceTimeStepper(
            value = repeatDelay.toLong(),
            titleResId = R.string.preference_title_direct_control_repeat_delay,
            summaryResId = R.string.preference_summary_direct_control_repeat_delay,
            min = 25,
            max = 1000,
            step = 25,
            onValueChanged = { v: Long ->
                repeatDelay = v.toInt()
                prefs.setLongValue(DirectControlSettings.KEY_REPEAT_DELAY, v)
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
