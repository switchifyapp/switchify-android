package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.service.techniques.cursor.CursorSettings

@Composable
fun PrecisionCursorSpeedStepper() {
    PreferenceTimeStepper(
        value = CursorSettings.getFineCursorScanRate(),
        titleResId = R.string.preference_title_precision_cursor_speed,
        summaryResId = R.string.preference_summary_precision_cursor_speed,
        explanationResId = R.string.feature_explanation_precision_cursor_speed,
        min = 100,
        max = 5000,
        step = 100,
        onValueChanged = { newValue ->
            CursorSettings.setFineCursorScanRate(newValue)
        }
    )
} 