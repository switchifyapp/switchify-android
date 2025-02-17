package com.enaboapps.switchify.screens.settings.scanning

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
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
    var moveRepeat by remember {
        mutableStateOf(
            preferenceManager.getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_MOVE_REPEAT,
                false
            )
        )
    }
    var moveRepeatDelay by remember {
        mutableLongStateOf(
            preferenceManager.getLongValue(
                PreferenceManager.PREFERENCE_KEY_MOVE_REPEAT_DELAY,
                500
            )
        )
    }

    BaseView(
        title = "Other Scan Settings",
        navController = navController
    ) {
        Section(title = "Timing") {
            PreferenceSwitch(
                title = "Auto-start scanning",
                summary = "Automatically start scanning after a selection",
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

        Section(title = "Behavior") {
            PreferenceSwitch(
                title = "First item pause",
                summary = "Pause briefly on the first item of each scan cycle",
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
                    title = "First item pause duration",
                    summary = "How long to pause on the first item",
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

        Section(title = "Manual Scan Behavior") {
            PreferenceSwitch(
                title = "Move Repeat",
                summary = "Hold down a switch to move repeatedly",
                checked = moveRepeat,
                onCheckedChange = {
                    moveRepeat = it
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_MOVE_REPEAT,
                        it
                    )
                }
            )

            if (moveRepeat) {
                PreferenceTimeStepper(
                    value = moveRepeatDelay,
                    title = "Move Repeat Delay",
                    summary = "The delay before repeating the last move action",
                    min = 100,
                    max = 2000,
                    step = 100,
                    onValueChanged = { newValue ->
                        moveRepeatDelay = newValue
                        preferenceManager.setLongValue(
                            PreferenceManager.PREFERENCE_KEY_MOVE_REPEAT_DELAY,
                            newValue
                        )
                    }
                )
            }
        }
    }
}