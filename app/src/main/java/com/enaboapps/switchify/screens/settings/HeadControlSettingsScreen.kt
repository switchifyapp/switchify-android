package com.enaboapps.switchify.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings

@Composable
fun HeadControlSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = PreferenceManager(context)
    val settings = HeadControlSettings(context)

    // Convert float values to integer indices for UI
    val sensitivityValues = floatArrayOf(0.1f, 0.3f, 0.5f, 0.8f, 1.0f, 1.3f, 1.6f, 2.0f)
    val deadzoneValues = floatArrayOf(0.1f, 0.3f, 0.5f, 0.8f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f)
    
    val currentSensitivity = settings.sensitivity()
    val currentDeadzone = settings.deadzone()
    
    // Find closest index for current values
    val sensitivityIndex = sensitivityValues.indexOfFirst { kotlin.math.abs(it - currentSensitivity) < 0.05f }.let { if (it == -1) 3 else it }
    val deadzoneIndex = deadzoneValues.indexOfFirst { kotlin.math.abs(it - currentDeadzone) < 0.05f }.let { if (it == -1) 2 else it }
    
    var sensitivity by remember { mutableIntStateOf(sensitivityIndex) }
    var deadzone by remember { mutableIntStateOf(deadzoneIndex) }
    BaseView(
        titleResId = R.string.screen_title_head_control_settings,
        navController = navController
    ) {
        Section(titleResId = R.string.section_title_head_control_movement) {
            PreferenceValueSelector(
                value = sensitivity,
                titleResId = R.string.preference_title_head_control_sensitivity,
                summaryResId = R.string.preference_summary_head_control_sensitivity,
                values = IntArray(sensitivityValues.size) { it },
                buttonLabelFormatter = { String.format("%.1f", sensitivityValues[it]) },
                displayFormatter = { String.format("%.1f", sensitivityValues[it]) },
                onValueChanged = { index ->
                    sensitivity = index
                    prefs.setFloatValue(HeadControlSettings.KEY_SENSITIVITY, sensitivityValues[index])
                }
            )
            
            PreferenceValueSelector(
                value = deadzone,
                titleResId = R.string.preference_title_head_control_deadzone,
                summaryResId = R.string.preference_summary_head_control_deadzone,
                values = IntArray(deadzoneValues.size) { it },
                buttonLabelFormatter = { String.format("%.1f°", deadzoneValues[it]) },
                displayFormatter = { String.format("%.1f°", deadzoneValues[it]) },
                onValueChanged = { index ->
                    deadzone = index
                    prefs.setFloatValue(HeadControlSettings.KEY_DEADZONE, deadzoneValues[index])
                }
            )
        }
    }
}