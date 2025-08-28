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

    var baseStep by remember { mutableIntStateOf(settings.baseStep()) }
    var maxStep by remember { mutableIntStateOf(settings.maxStep()) }
    var accelInc by remember { mutableIntStateOf(settings.accelIncrement()) }
    var precisionEnabled by remember { mutableStateOf(settings.precisionEnabled()) }
    var precisionPct by remember {
        // store multiplier as percent (e.g., 50 for 0.5)
        mutableIntStateOf((settings.precisionMultiplier() * 100f).toInt())
    }

    Section(titleResId = R.string.section_title_direct_control_movement) {
        PreferenceTimeStepper(
            value = baseStep.toLong(),
            titleResId = R.string.preference_title_direct_control_base_step,
            summaryResId = R.string.preference_summary_direct_control_base_step,
            min = 5,
            max = 50,
            step = 1,
            onValueChanged = { v ->
                baseStep = v.toInt()
                prefs.setIntegerValue(DirectControlSettings.KEY_BASE_STEP, baseStep)
            }
        )
        PreferenceTimeStepper(
            value = maxStep.toLong(),
            titleResId = R.string.preference_title_direct_control_max_step,
            summaryResId = R.string.preference_summary_direct_control_max_step,
            min = 20,
            max = 200,
            step = 5,
            onValueChanged = { v ->
                maxStep = v.toInt()
                prefs.setIntegerValue(DirectControlSettings.KEY_MAX_STEP, maxStep)
            }
        )
        PreferenceTimeStepper(
            value = accelInc.toLong(),
            titleResId = R.string.preference_title_direct_control_accel_increment,
            summaryResId = R.string.preference_summary_direct_control_accel_increment,
            min = 1,
            max = 20,
            step = 1,
            onValueChanged = { v ->
                accelInc = v.toInt()
                prefs.setIntegerValue(DirectControlSettings.KEY_ACCEL_INCREMENT, accelInc)
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

        PreferenceTimeStepper(
            value = precisionPct.toLong(),
            titleResId = R.string.preference_title_direct_control_precision_multiplier,
            summaryResId = R.string.preference_summary_direct_control_precision_multiplier,
            min = 25,
            max = 100,
            step = 5,
            onValueChanged = { v ->
                precisionPct = v.toInt()
                val mul = (precisionPct.coerceIn(1, 100) / 100f)
                prefs.setFloatValue(DirectControlSettings.KEY_PRECISION_MULTIPLIER, mul)
            }
        )
    }
}

