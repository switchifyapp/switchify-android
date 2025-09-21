package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings

@Composable
fun PrecisionPointScanSpeedStepper() {
    val context = LocalContext.current
    PreferenceValueSelector(
        value = PointScanSettings.getLineSpeedLevel(),
        titleResId = R.string.preference_title_precision_point_scan_speed,
        summaryResId = R.string.preference_summary_precision_point_scan_speed,
        min = 1,
        max = 25,
        buttonLabelFormatter = { speedLevel -> speedLevel.toString() },
        displayFormatter = { speedLevel ->
            "Level $speedLevel (${PointScanSettings.getSpeedLevelDescription(speedLevel)})"
        },
        onValueChanged = { newSpeedLevel ->
            PointScanSettings.setLineSpeedLevel(newSpeedLevel, context)
        }
    )
}
