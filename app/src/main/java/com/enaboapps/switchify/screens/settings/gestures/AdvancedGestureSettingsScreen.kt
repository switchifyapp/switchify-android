package com.enaboapps.switchify.screens.settings.gestures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.gestures.GestureRepeatManager

@Composable
fun AdvancedGestureSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val preferenceManager = PreferenceManager(context)
    val gestureRepeatInitialDelayState = remember {
        mutableStateOf(
            preferenceManager.getLongValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_REPEAT_INITIAL_DELAY,
                GestureRepeatManager.DEFAULT_INITIAL_REPEAT_DELAY
            )
        )
    }
    val gestureRepeatDelayState = remember {
        mutableStateOf(
            preferenceManager.getLongValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_REPEAT_DELAY,
                GestureRepeatManager.DEFAULT_REPEAT_DELAY
            )
        )
    }

    BaseView(
        titleResId = R.string.screen_title_advanced_gestures,
        navController = navController
    ) {
        Section(titleResId = R.string.settings_section_advanced_gestures) {
            PreferenceTimeStepper(
                value = gestureRepeatInitialDelayState.value,
                titleResId = R.string.preference_title_gesture_repeat_initial_delay,
                summaryResId = R.string.preference_summary_gesture_repeat_initial_delay,
                min = GestureRepeatManager.MIN_INITIAL_REPEAT_DELAY,
                max = GestureRepeatManager.MAX_INITIAL_REPEAT_DELAY,
                step = GestureRepeatManager.INITIAL_REPEAT_DELAY_STEP,
                onValueChanged = {
                    preferenceManager.setLongValue(
                        PreferenceManager.PREFERENCE_KEY_GESTURE_REPEAT_INITIAL_DELAY,
                        it
                    )
                    gestureRepeatInitialDelayState.value = it
                }
            )
            PreferenceTimeStepper(
                value = gestureRepeatDelayState.value,
                titleResId = R.string.preference_title_gesture_repeat_delay,
                summaryResId = R.string.preference_summary_gesture_repeat_delay,
                min = GestureRepeatManager.MIN_REPEAT_DELAY,
                max = GestureRepeatManager.MAX_REPEAT_DELAY,
                step = GestureRepeatManager.REPEAT_DELAY_STEP,
                onValueChanged = {
                    preferenceManager.setLongValue(
                        PreferenceManager.PREFERENCE_KEY_GESTURE_REPEAT_DELAY,
                        it
                    )
                    gestureRepeatDelayState.value = it
                }
            )
        }
    }
}
