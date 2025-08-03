package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.service.techniques.cursor.CursorSettings

@Composable
fun SingleCursorSpeedStepper() {
    val context = LocalContext.current
    PreferenceTimeStepper(
        value = CursorSettings.getFineCursorScanRate(),
        titleResId = R.string.preference_title_single_cursor_speed,
        summaryResId = R.string.preference_summary_single_cursor_speed,
        explanationResId = R.string.feature_explanation_single_cursor_speed,
        min = 25,
        max = 5000,
        step = 25,
        onValueChanged = { newValue ->
            CursorSettings.setFineCursorScanRate(newValue, context)
        }
    )
} 