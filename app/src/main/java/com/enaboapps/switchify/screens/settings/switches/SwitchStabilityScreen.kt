package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.screens.settings.switches.models.SwitchStabilityScreenModel

@Composable
fun SwitchStabilityScreen(navController: NavController) {
    val verticalScrollState = rememberScrollState()
    val context = LocalContext.current
    val switchStabilityScreenModel = SwitchStabilityScreenModel(context)
    val ignoredRepeat = switchStabilityScreenModel.switchIgnoreRepeat.observeAsState()
    BaseView(
        titleResId = R.string.screen_title_switch_stability,
        navController = navController
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.Top
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
        }
    }
}