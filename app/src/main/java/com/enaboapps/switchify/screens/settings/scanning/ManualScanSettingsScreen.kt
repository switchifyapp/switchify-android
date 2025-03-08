package com.enaboapps.switchify.screens.settings.scanning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
fun ManualScanSettingsScreen(navController: NavController) {
    val preferenceManager = PreferenceManager(LocalContext.current)

    // states
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
        titleResId = R.string.screen_title_manual_settings,
        navController = navController
    ) {
        Section(titleResId = R.string.section_title_manual_scan_behavior) {
            PreferenceSwitch(
                titleResId = R.string.preference_title_move_repeat,
                summaryResId = R.string.preference_summary_move_repeat,
                checked = moveRepeat,
                onCheckedChange = {
                    moveRepeat = it
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_MOVE_REPEAT,
                        it
                    )
                }
            )

            AnimatedVisibility(
                visible = moveRepeat,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PreferenceTimeStepper(
                    value = moveRepeatDelay,
                    titleResId = R.string.preference_title_move_repeat_delay,
                    summaryResId = R.string.preference_summary_move_repeat_delay,
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