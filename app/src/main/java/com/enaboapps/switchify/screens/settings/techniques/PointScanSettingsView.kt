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
import com.enaboapps.switchify.components.Picker
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.shared.BlockCountSection
import com.enaboapps.switchify.screens.settings.shared.BlockPointScanSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.PrecisionPointScanSpeedStepper
import com.enaboapps.switchify.screens.settings.shared.SinglePointScanSpeedStepper
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings

@Composable
fun PointScanSettingsView() {
    val context = LocalContext.current
    PointScanSettings.init(context)
    val cursorModes = listOf(
        PointScanSettings.Modes.MODE_SINGLE,
        PointScanSettings.Modes.MODE_BLOCK
    )
    var currentMode by remember { mutableStateOf(PointScanSettings.getMode()) }

    val setCursorMode = { mode: String ->
        PointScanSettings.setMode(mode)
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
        BlockCountSection(
            initialBlockCount = PointScanSettings.getCursorBlockCount(),
            onBlockCountChanged = { PointScanSettings.setCursorBlockCount(it) }
        )
    }
}
