package com.enaboapps.switchify.screens.pc

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
import com.enaboapps.switchify.pc.PcMouseRepeatDefaults

@Composable
fun PcSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val preferenceManager = remember(context) { PreferenceManager(context) }
    var mouseRepeat by remember {
        mutableStateOf(
            preferenceManager.getBooleanValue(
                PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT,
                true
            )
        )
    }
    var repeatInterval by remember {
        mutableLongStateOf(
            preferenceManager.getLongValue(
                PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT_INTERVAL,
                PcMouseRepeatDefaults.DEFAULT_INTERVAL_MS
            )
        )
    }

    BaseView(
        titleResId = R.string.pc_settings_title,
        navController = navController
    ) {
        Section(titleResId = R.string.pc_settings_mouse_section) {
            PreferenceSwitch(
                titleResId = R.string.pc_settings_mouse_repeat_title,
                summaryResId = R.string.pc_settings_mouse_repeat_summary,
                checked = mouseRepeat,
                onCheckedChange = {
                    mouseRepeat = it
                    preferenceManager.setBooleanValue(
                        PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT,
                        it
                    )
                }
            )

            if (mouseRepeat) {
                PreferenceTimeStepper(
                    value = repeatInterval,
                    titleResId = R.string.pc_settings_mouse_repeat_interval_title,
                    summaryResId = R.string.pc_settings_mouse_repeat_interval_summary,
                    min = PcMouseRepeatDefaults.MIN_INTERVAL_MS,
                    max = PcMouseRepeatDefaults.MAX_INTERVAL_MS,
                    step = PcMouseRepeatDefaults.INTERVAL_STEP_MS,
                    onValueChanged = {
                        repeatInterval = it
                        preferenceManager.setLongValue(
                            PreferenceManager.PREFERENCE_KEY_PC_MOUSE_REPEAT_INTERVAL,
                            it
                        )
                    }
                )
            }
        }
    }
}
