package com.enaboapps.switchify.screens.settings.techniques

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.enaboapps.switchify.utils.Resources

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

    AnimatedVisibility(
        visible = currentMode == CursorMode.Modes.MODE_BLOCK,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        BlockSettingsView()
    }
}

@Composable
private fun BlockSettingsView() {
    val preferenceManager = PreferenceManager(LocalContext.current)
    var currentBlockCount by remember {
        mutableIntStateOf(
            preferenceManager.getStringValue(
                PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
                "4"
            ).toInt()
        )
    }
    val blockCounts = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10")
    Section(titleResId = R.string.section_title_block_settings) {
        Picker(
            titleResId = R.string.preference_title_block_count,
            selectedItem = currentBlockCount.toString(),
            items = blockCounts,
            onItemSelected = { item ->
                currentBlockCount = item.toInt()
                preferenceManager.setStringValue(
                    PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
                    item
                )
            },
            itemToString = { it.toString() },
            itemDescription = { Resources.getString(R.string.preference_summary_block_count) }
        )
    }
}