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
import com.enaboapps.switchify.screens.settings.shared.BlockPointScanSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.PrecisionPointScanSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.SinglePointScanSpeedStepper
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings
import com.enaboapps.switchify.utils.Resources

@Composable
fun PointScanSettingsView() {
    val context = LocalContext.current
    PointScanSettings.init(context)
    val cursorModes = listOf(
        PointScanSettings.Modes.MODE_SINGLE,
        PointScanSettings.Modes.MODE_BLOCK
    )
    val preferenceManager = PreferenceManager(context)
    var currentMode by remember { mutableStateOf(PointScanSettings.getMode()) }

    val setCursorMode = { mode: String ->
        PointScanSettings.setMode(mode, context)
        currentMode = mode
    }
    Section(titleResId = R.string.section_title_point_scan_mode) {
        Picker(
            titleResId = R.string.picker_title_select_point_scan_mode,
            selectedItem = currentMode,
            items = cursorModes,
            onItemSelected = setCursorMode,
            itemToString = { PointScanSettings.getModeName(it) },
            itemDescription = { PointScanSettings.getModeDescription(it) }
        )
    }

    Section(titleResId = R.string.section_title_point_scan_speed) {
        AnimatedVisibility(
            visible = currentMode == PointScanSettings.Modes.MODE_SINGLE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SinglePointScanSpeedStepper()
        }

        AnimatedVisibility(
            visible = currentMode == PointScanSettings.Modes.MODE_BLOCK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PrecisionPointScanSpeedStepper()
        }

        AnimatedVisibility(
            visible = currentMode == PointScanSettings.Modes.MODE_BLOCK,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BlockPointScanSpeedStepper()
        }
    }

    AnimatedVisibility(
        visible = currentMode == PointScanSettings.Modes.MODE_BLOCK,
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
            PointScanSettings.getCursorBlockCount()
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
                PointScanSettings.setCursorBlockCount(item.toInt(), context)
            },
            itemToString = { it.toString() },
            itemDescription = { value -> Resources.getString(R.string.preference_summary_grid_size_dynamic, value) }
        )
    }
}
