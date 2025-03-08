package com.enaboapps.switchify.screens.settings.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.PreferenceTimeStepper

@Composable
fun ItemScanSpeedStepper() {
    val preferenceManager = PreferenceManager(LocalContext.current)

    PreferenceTimeStepper(
        value = preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_SCAN_RATE,
            1000
        ),
        titleResId = R.string.preference_title_item_scan_speed,
        summaryResId = R.string.preference_summary_item_scan_speed,
        min = 200,
        max = 10000,
        step = 100,
        onValueChanged = { newValue ->
            preferenceManager.setLongValue(
                PreferenceManager.PREFERENCE_KEY_SCAN_RATE,
                newValue
            )
        }
    )
} 