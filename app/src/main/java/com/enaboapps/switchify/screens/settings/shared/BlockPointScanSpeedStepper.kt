package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings

@Composable
fun BlockPointScanSpeedStepper() {
    val context = LocalContext.current
    val preferenceManager = PreferenceManager(context)
    PointScanSettings.init(context)

    PreferenceTimeStepper(
        value = preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE,
            1000
        ),
        titleResId = R.string.preference_title_block_point_scan_speed,
        summaryResId = R.string.preference_summary_block_point_scan_speed,
        explanationResId = R.string.feature_explanation_block_point_scan_speed,
        min = 100,
        max = 5000,
        step = 100,
        onValueChanged = { newValue ->
            PointScanSettings.setCursorBlockScanRate(newValue)
        }
    )
}
