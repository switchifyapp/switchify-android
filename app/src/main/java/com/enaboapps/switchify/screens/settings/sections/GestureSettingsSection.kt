package com.enaboapps.switchify.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.nav.NavigationRoute

@Composable
fun GesturesSettingsSection(navController: NavController) {
    val context = LocalContext.current
    val preferenceManager = PreferenceManager(context)
    val gestureLockAutoReenableState = remember {
        mutableStateOf(
            preferenceManager.getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_GESTURE_LOCK_AUTO_REENABLE,
                false
            )
        )
    }
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

    Section(titleResId = R.string.settings_section_gesture) {
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
        NavRouteLink(
            titleResId = R.string.screen_title_gesture_patterns,
            summaryResId = R.string.gesture_patterns_section_summary,
            navController = navController,
            route = NavigationRoute.GesturePatterns.name
        )
        NavRouteLink(
            titleResId = R.string.screen_title_scrolling_settings,
            summaryResId = R.string.settings_summary_scrolling_settings,
            navController = navController,
            route = NavigationRoute.ScrollingSettings.name
        )
    }
}
