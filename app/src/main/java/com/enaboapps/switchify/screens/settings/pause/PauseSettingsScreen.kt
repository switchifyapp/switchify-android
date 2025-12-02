package com.enaboapps.switchify.screens.settings.pause

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.PreferenceValueSelector
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
        PreferenceValueSelector(
            value = pauseSettingsScreenModel.pauseTimeout.observeAsState().value ?: 30000,
            titleResId = R.string.preference_title_pause_timeout,
            summaryResId = R.string.preference_summary_pause_timeout,
            values = intArrayOf(30000, 60000, 120000, 180000, 240000, 300000),
            buttonLabelFormatter = { value ->
                when (value) {
                    30000 -> "30s"
                    60000 -> "1m"
                    120000 -> "2m"
                    180000 -> "3m"
                    240000 -> "4m"
                    300000 -> "5m"
                    else -> "${value / 1000}s"
                }
            },
            displayFormatter = { value ->
                when (value) {
                    30000 -> "30 seconds"
                    60000 -> "1 minute"
                    120000 -> "2 minutes"
                    180000 -> "3 minutes"
                    240000 -> "4 minutes"
                    300000 -> "5 minutes"
                    else -> "${value / 1000} seconds"
                }
            },
            onValueChanged = {
                pauseSettingsScreenModel.setPauseTimeout(it)
            }
        )
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
