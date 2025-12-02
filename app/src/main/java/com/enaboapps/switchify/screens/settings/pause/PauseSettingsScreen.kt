package com.enaboapps.switchify.screens.settings.pause

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.screens.settings.pause.models.PauseSettingsScreenModel

@Composable
fun PauseSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val pauseSettingsScreenModel: PauseSettingsScreenModel =
        viewModel { PauseSettingsScreenModel(context) }
    BaseView(
        titleResId = R.string.screen_title_pause_settings,
        navController = navController
    ) {
        PreferenceTimeStepper(
            titleResId = R.string.preference_title_pause_timeout,
            summaryResId = R.string.preference_summary_pause_timeout,
            min = 30000,
            max = 300000,
            value = pauseSettingsScreenModel.pauseTimeout.observeAsState().value ?: 30000
        ) {
            pauseSettingsScreenModel.setPauseTimeout(it)
        }
        PreferenceTimeStepper(
            titleResId = R.string.preference_title_hold_to_unpause_duration,
            summaryResId = R.string.preference_summary_hold_to_unpause_duration,
            min = 500,
            max = 5000,
            value = pauseSettingsScreenModel.holdToUnpauseDuration.observeAsState().value ?: 2000
        ) {
            pauseSettingsScreenModel.setHoldToUnpauseDuration(it)
        }
    }
}
