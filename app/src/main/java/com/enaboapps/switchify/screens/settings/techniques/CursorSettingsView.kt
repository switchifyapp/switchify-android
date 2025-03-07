package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.techniques.cursor.CursorMode

@Composable
fun CursorSettingsView() {
    CursorMode.init(LocalContext.current)
    val cursorModes = listOf(
        CursorMode.Modes.MODE_SINGLE,
        CursorMode.Modes.MODE_BLOCK
    )
    val preferenceManager = PreferenceManager(LocalContext.current)
    var currentMode by remember { mutableStateOf(CursorMode.getMode()) }

    val setCursorMode = { mode: String ->
        preferenceManager.setStringValue(PreferenceManager.PREFERENCE_KEY_CURSOR_MODE, mode)
        currentMode = mode
    }
    Section(titleResId = R.string.section_title_cursor_mode) {
        Picker(
            titleResId = R.string.picker_title_select_cursor_mode,
            selectedItem = currentMode,
            items = cursorModes,
            onItemSelected = setCursorMode,
            itemToString = { CursorMode.getModeName(it) },
            itemDescription = { CursorMode.getModeDescription(it) }
        )
    }

    Section(titleResId = R.string.section_title_cursor_speed) {
        AnimatedVisibility(
            visible = currentMode == CursorMode.Modes.MODE_SINGLE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PreferenceTimeStepper(
                value = preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CURSOR_FINE_SCAN_RATE,
                    1000
                ),
                titleResId = R.string.preference_title_single_cursor_speed,
                summaryResId = R.string.preference_summary_single_cursor_speed,
                explanationResId = R.string.feature_explanation_single_cursor_speed,
                min = 25,
                max = 5000,
                step = 25,
                onValueChanged = { newValue ->
                    preferenceManager.setLongValue(
                        PreferenceManager.PREFERENCE_KEY_CURSOR_FINE_SCAN_RATE,
                        newValue
                    )
                }
            )
        }

        AnimatedVisibility(
            visible = currentMode == CursorMode.Modes.MODE_BLOCK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PreferenceTimeStepper(
                value = preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CURSOR_FINE_SCAN_RATE,
                    1000
                ),
                titleResId = R.string.preference_title_precision_cursor_speed,
                summaryResId = R.string.preference_summary_precision_cursor_speed,
                explanationResId = R.string.feature_explanation_precision_cursor_speed,
                min = 100,
                max = 5000,
                step = 100,
                onValueChanged = { newValue ->
                    preferenceManager.setLongValue(
                        PreferenceManager.PREFERENCE_KEY_CURSOR_FINE_SCAN_RATE,
                        newValue
                    )
                }
            )
        }
        AnimatedVisibility(
            visible = currentMode == CursorMode.Modes.MODE_BLOCK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PreferenceTimeStepper(
                value = preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE,
                    1000
                ),
                titleResId = R.string.preference_title_block_cursor_speed,
                summaryResId = R.string.preference_summary_block_cursor_speed,
                explanationResId = R.string.feature_explanation_block_cursor_speed,
                min = 100,
                max = 5000,
                step = 100,
                onValueChanged = { newValue ->
                    preferenceManager.setLongValue(
                        PreferenceManager.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE,
                        newValue
                    )
                }
            )
        }
    }
}