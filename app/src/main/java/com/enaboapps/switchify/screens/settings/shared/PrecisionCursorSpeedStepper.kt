package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.PreferenceTimeStepper

@Composable
fun PrecisionCursorSpeedStepper() {
    val preferenceManager = PreferenceManager(LocalContext.current)
    
    PreferenceTimeStepper(
        value = preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_CURSOR_FINE_SCAN_RATE,
            1000
        ),
        titleResId = R.string.preference_title_precision_cursor_speed,
        summaryResId = R.string.preference_summary_precision_cursor_speed,
        explanationResId = R.string.feature_explanation_precision_cursor_speed,
        min = 100,
        max = 5000,
        step = 100,
        onValueChanged = { newValue ->
            preferenceManager.setLongValue(
                PreferenceManager.PREFERENCE_KEY_CURSOR_FINE_SCAN_RATE,
                newValue
            )
        }
    )
} 