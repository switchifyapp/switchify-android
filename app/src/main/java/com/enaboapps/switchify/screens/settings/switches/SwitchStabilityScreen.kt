package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.screens.settings.switches.models.SwitchStabilityScreenModel

@Composable
fun SwitchStabilityScreen(navController: NavController) {
    val context = LocalContext.current
    val switchStabilityScreenModel: SwitchStabilityScreenModel =
        viewModel { SwitchStabilityScreenModel(context) }
    val ignoredRepeat = switchStabilityScreenModel.switchIgnoreRepeat.observeAsState()
    BaseView(
        titleResId = R.string.screen_title_switch_stability,
        navController = navController
    ) {
        PreferenceSwitch(
            titleResId = R.string.preference_title_ignore_repeat,
            summaryResId = R.string.preference_summary_ignore_repeat,
            checked = ignoredRepeat.value == true
        ) {
            switchStabilityScreenModel.setSwitchIgnoreRepeat(it)
        }
        if (ignoredRepeat.value == true) {
            PreferenceTimeStepper(
                titleResId = R.string.preference_title_ignore_repeat_delay,
                summaryResId = R.string.preference_summary_ignore_repeat_delay,
                min = 100,
                max = 10000,
                value = switchStabilityScreenModel.switchIgnoreRepeatDelay.observeAsState().value
                    ?: 0
            ) {
                switchStabilityScreenModel.setSwitchIgnoreRepeatDelay(it)
            }
        }
        PreferenceTimeStepper(
            titleResId = R.string.preference_title_switch_hold_time,
            summaryResId = R.string.preference_summary_switch_hold_time,
            min = 100,
            max = 10000,
            value = switchStabilityScreenModel.switchHoldTime.observeAsState().value ?: 0
        ) {
            switchStabilityScreenModel.setSwitchHoldTime(it)
        }
        PreferenceTimeStepper(
            titleResId = R.string.preference_title_hold_to_unpause_duration,
            summaryResId = R.string.preference_summary_hold_to_unpause_duration,
            min = 500,
            max = 5000,
            value = switchStabilityScreenModel.holdToUnpauseDuration.observeAsState().value ?: 2000
        ) {
            switchStabilityScreenModel.setHoldToUnpauseDuration(it)
        }
    }
}