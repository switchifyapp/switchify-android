package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import com.enaboapps.switchify.components.PreferenceTimeStepper

@Composable
fun BlockScanSpeedStepper(
    value: Long,
    titleResId: Int,
    summaryResId: Int,
    explanationResId: Int,
    onValueChanged: (Long) -> Unit
) {
    PreferenceTimeStepper(
        value = value,
        titleResId = titleResId,
        summaryResId = summaryResId,
        explanationResId = explanationResId,
        min = 100,
        max = 5000,
        step = 100,
        onValueChanged = onValueChanged
    )
}
