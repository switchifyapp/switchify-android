package com.enaboapps.switchify.screens.settings.scanning

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.Section

@Composable
fun RadarSettingsScreen(navController: NavController) {
    val preferenceManager = PreferenceManager(LocalContext.current)

    BaseView(
        title = "Radar Settings",
        navController = navController
    ) {
        Section(title = "Radar Speed") {
            PreferenceTimeStepper(
                value = preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_RADAR_SCAN_RATE,
                    1000
                ),
                title = "Radar speed",
                summary = "How fast the radar moves",
                min = 10,
                max = 5000,
                step = 10,
                onValueChanged = { newValue ->
                    preferenceManager.setLongValue(
                        PreferenceManager.PREFERENCE_KEY_RADAR_SCAN_RATE,
                        newValue
                    )
                }
            )
        }
    }
} 