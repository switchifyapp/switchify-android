package com.enaboapps.switchify.screens.settings.scanning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section

@Composable
fun OtherScanSettingsScreen(navController: NavController) {
    val preferenceManager = PreferenceManager(LocalContext.current)

    // states
    var autoStartScanning by remember {
        mutableStateOf(
            preferenceManager.getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_AUTOMATICALLY_START_SCAN_AFTER_SELECTION,
                true
            )
        )
    }
    var pauseOnFirstItem by remember {
        mutableStateOf(
            preferenceManager.getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM,
                false
            )
        )
    }
    var pauseOnFirstItemDelay by remember {
        mutableLongStateOf(
            preferenceManager.getLongValue(
                PreferenceManager.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM_DELAY,
                1000
            )
        )
    }
    BaseView(
        titleResId = R.string.screen_title_other_scan_settings,
        navController = navController
    ) {
        Section(titleResId = R.string.section_title_timing) {
            PreferenceSwitch(
                titleResId = R.string.preference_title_auto_start_scanning,
                summaryResId = R.string.preference_summary_auto_start_scanning,
                checked = autoStartScanning,
                onCheckedChange = {
                    autoStartScanning = it
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_AUTOMATICALLY_START_SCAN_AFTER_SELECTION,
                        it
                    )
                }
            )
        }

        Section(titleResId = R.string.section_title_behavior) {
            PreferenceSwitch(
                titleResId = R.string.preference_title_first_item_pause,
                summaryResId = R.string.preference_summary_first_item_pause,
                checked = pauseOnFirstItem,
                onCheckedChange = {
                    pauseOnFirstItem = it
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM,
                        it
                    )
                }
            )

            if (pauseOnFirstItem) {
                PreferenceTimeStepper(
                    value = pauseOnFirstItemDelay,
                    titleResId = R.string.preference_title_first_item_pause_duration,
                    summaryResId = R.string.preference_summary_first_item_pause_duration,
                    min = 500,
                    max = 3000,
                    step = 100,
                    onValueChanged = { newValue ->
                        pauseOnFirstItemDelay = newValue
                        preferenceManager.setLongValue(
                            PreferenceManager.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM_DELAY,
                            newValue
                        )
                    }
                )
            }
        }
    }
}