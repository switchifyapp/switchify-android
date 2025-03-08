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
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.BlockCursorSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.PrecisionCursorSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.SingleCursorSpeedStepper
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
            SingleCursorSpeedStepper()
        }

        AnimatedVisibility(
            visible = currentMode == CursorMode.Modes.MODE_BLOCK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PrecisionCursorSpeedStepper()
        }
        
        AnimatedVisibility(
            visible = currentMode == CursorMode.Modes.MODE_BLOCK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BlockCursorSpeedStepper()
        }
    }
}