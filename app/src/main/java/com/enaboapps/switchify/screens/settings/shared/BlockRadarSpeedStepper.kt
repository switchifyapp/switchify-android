package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.techniques.radar.RadarSettings

@Composable
fun BlockRadarSpeedStepper() {
    RadarSettings.init(LocalContext.current)

    BlockScanSpeedStepper(
        value = RadarSettings.getBlockScanRate(),
        titleResId = R.string.preference_title_block_radar_speed,
        summaryResId = R.string.preference_summary_block_radar_speed,
        explanationResId = R.string.feature_explanation_block_radar_speed,
        onValueChanged = { RadarSettings.setBlockScanRate(it) }
    )
}
