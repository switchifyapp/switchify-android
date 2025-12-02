package com.enaboapps.switchify.screens.settings.pause

import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
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
        PreferenceValueSelector(
            value = pauseSettingsScreenModel.holdToUnpauseDuration.observeAsState().value ?: 2000,
            titleResId = R.string.preference_title_hold_to_unpause_duration,
            summaryResId = R.string.preference_summary_hold_to_unpause_duration,
            values = intArrayOf(500, 1000, 2000, 3000, 5000),
            buttonLabelFormatter = { value ->
                when (value) {
                    500 -> "0.5s"
                    1000 -> "1s"
                    2000 -> "2s"
                    3000 -> "3s"
                    5000 -> "5s"
                    else -> "${value / 1000.0}s"
                }
            },
            displayFormatter = { value ->
                when (value) {
                    500 -> "0.5 seconds"
                    1000 -> "1 second"
                    2000 -> "2 seconds"
                    3000 -> "3 seconds"
                    5000 -> "5 seconds"
                    else -> "${value / 1000.0} seconds"
                }
            },
            onValueChanged = {
                pauseSettingsScreenModel.setHoldToUnpauseDuration(it)
            }
        )
    }
}
