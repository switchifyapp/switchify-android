package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings

@Composable
fun PrecisionPointScanSpeedStepper() {
    val context = LocalContext.current
    PreferenceTimeStepper(
        value = PointScanSettings.getFineCursorScanRate(),
        titleResId = R.string.preference_title_precision_point_scan_speed,
        summaryResId = R.string.preference_summary_precision_point_scan_speed,
        explanationResId = R.string.feature_explanation_precision_point_scan_speed,
        min = 100,
        max = 5000,
        step = 100,
        onValueChanged = { newValue ->
            PointScanSettings.setFineCursorScanRate(newValue, context)
        }
    )
}
