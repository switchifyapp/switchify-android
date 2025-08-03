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
import com.enaboapps.switchify.service.techniques.cursor.CursorSettings
import com.enaboapps.switchify.utils.Resources

@Composable
fun CursorSettingsView() {
    val context = LocalContext.current
    CursorSettings.init(context)
    val cursorModes = listOf(
        CursorSettings.Modes.MODE_SINGLE,
        CursorSettings.Modes.MODE_BLOCK
    )
    val preferenceManager = PreferenceManager(context)
    var currentMode by remember { mutableStateOf(CursorSettings.getMode()) }

    val setCursorMode = { mode: String ->
        CursorSettings.setMode(mode, context)
        currentMode = mode
    }
    Section(titleResId = R.string.section_title_cursor_mode) {
        Picker(
            titleResId = R.string.picker_title_select_cursor_mode,
            selectedItem = currentMode,
            items = cursorModes,
            onItemSelected = setCursorMode,
            itemToString = { CursorSettings.getModeName(it) },
            itemDescription = { CursorSettings.getModeDescription(it) }
        )
    }

    Section(titleResId = R.string.section_title_cursor_speed) {
        AnimatedVisibility(
            visible = currentMode == CursorSettings.Modes.MODE_SINGLE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SingleCursorSpeedStepper()
        }

        AnimatedVisibility(
            visible = currentMode == CursorSettings.Modes.MODE_BLOCK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PrecisionCursorSpeedStepper()
        }

        AnimatedVisibility(
            visible = currentMode == CursorSettings.Modes.MODE_BLOCK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BlockCursorSpeedStepper()
        }
    }

    AnimatedVisibility(
        visible = currentMode == CursorSettings.Modes.MODE_BLOCK,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        BlockSettingsView()
    }
}

@Composable
private fun BlockSettingsView() {
    val context = LocalContext.current
    var currentBlockCount by remember {
        mutableIntStateOf(
            CursorSettings.getCursorBlockCount()
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
                CursorSettings.setCursorBlockCount(item.toInt(), context)
            },
            itemToString = { it.toString() },
            itemDescription = { Resources.getString(R.string.preference_summary_block_count) }
        )
    }
}