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
import com.enaboapps.switchify.screens.settings.switches.models.SwitchHoldScreenModel

@Composable
fun SwitchHoldScreen(navController: NavController) {
    val context = LocalContext.current
    val model: SwitchHoldScreenModel = viewModel { SwitchHoldScreenModel(context) }
    val enabled = model.switchHoldEnabled.observeAsState(true)

    BaseView(
        titleResId = R.string.screen_title_switch_hold,
        navController = navController
    ) {
        PreferenceSwitch(
            titleResId = R.string.preference_title_switch_hold_enabled,
            summaryResId = R.string.preference_summary_switch_hold_enabled,
            checked = enabled.value
        ) {
            model.setSwitchHoldEnabled(it)
        }
        if (enabled.value) {
            PreferenceTimeStepper(
                titleResId = R.string.preference_title_switch_hold_time,
                summaryResId = R.string.preference_summary_switch_hold_time,
                min = 100,
                max = 10000,
                value = model.switchHoldTime.observeAsState().value ?: 1000
            ) {
                model.setSwitchHoldTime(it)
            }
        }
    }
}
