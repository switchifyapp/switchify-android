package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings

@Composable
fun BlockPointScanSpeedStepper() {
    PointScanSettings.init(LocalContext.current)

    BlockScanSpeedStepper(
        value = PointScanSettings.getCursorBlockScanRate(),
        titleResId = R.string.preference_title_block_point_scan_speed,
        summaryResId = R.string.preference_summary_block_point_scan_speed,
        explanationResId = R.string.feature_explanation_block_point_scan_speed,
        onValueChanged = { PointScanSettings.setCursorBlockScanRate(it) }
    )
}
