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

@Composable
fun GestureSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val preferenceManager = PreferenceManager(context)
    val autoScroll =
        remember { preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SCROLL) }
    val autoScrollDelay =
        remember { preferenceManager.getLongValue(PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SCROLL_DELAY) }

    val autoScrollState = remember { mutableStateOf(autoScroll) }

    BaseView(
        titleResId = R.string.screen_title_gesture_settings,
        navController = navController
    ) {
        PreferenceSwitch(
            titleResId = R.string.preference_title_auto_scroll,
            summaryResId = R.string.preference_summary_auto_scroll,
            checked = autoScroll,
            isRestrictedToPro = true,
            onCheckedChange = {
                preferenceManager.setBooleanValue(
                    PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SCROLL,
                    it
                )
                autoScrollState.value = it
            }
        )
        if (autoScrollState.value) {
            PreferenceTimeStepper(
                titleResId = R.string.preference_title_auto_scroll_delay,
                summaryResId = R.string.preference_summary_auto_scroll_delay,
                min = 100,
                max = 10000,
                value = autoScrollDelay,
                onValueChanged = {
                    preferenceManager.setLongValue(
                        PreferenceManager.Keys.PREFERENCE_KEY_AUTO_SCROLL_DELAY,
                        it
                    )
                }
            )
        }
    }
}