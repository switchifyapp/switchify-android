package com.enaboapps.switchify.screens.settings.gestures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.gestures.GestureRepeatManager

@Composable
fun AdvancedGestureSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val preferenceManager = PreferenceManager(context)
    val gestureRepeatState = remember {
        mutableStateOf(
            preferenceManager.getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_REPEAT,
                false
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
    val gestureLockAutoReenableState = remember {
        mutableStateOf(
            preferenceManager.getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_LOCK_AUTO_REENABLE,
                false
            )
        )
    }

    BaseView(
        titleResId = R.string.screen_title_advanced_gestures,
        navController = navController
    ) {
        Section(titleResId = R.string.settings_section_advanced_gestures) {
            PreferenceSwitch(
                titleResId = R.string.preference_title_gesture_repeat,
                summaryResId = R.string.preference_summary_gesture_repeat,
                checked = gestureRepeatState.value,
                onCheckedChange = {
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_GESTURE_REPEAT,
                        it
                    )
                    gestureRepeatState.value = it
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
            PreferenceSwitch(
                titleResId = R.string.preference_title_gesture_lock_auto_reenable,
                summaryResId = R.string.preference_summary_gesture_lock_auto_reenable,
                checked = gestureLockAutoReenableState.value,
                onCheckedChange = {
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_GESTURE_LOCK_AUTO_REENABLE,
                        it
                    )
                    gestureLockAutoReenableState.value = it
                }
            )
        }
    }
}
