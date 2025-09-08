package com.enaboapps.switchify.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture

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
    
    // Gesture selection state
    val availableGestures = settings.getAvailableSelectGestures()
    val gestureNames = mapOf(
        CameraSwitchFacialGesture.SMILE to context.getString(R.string.head_control_gesture_smile),
        CameraSwitchFacialGesture.LEFT_WINK to context.getString(R.string.head_control_gesture_left_wink),
        CameraSwitchFacialGesture.RIGHT_WINK to context.getString(R.string.head_control_gesture_right_wink),
        CameraSwitchFacialGesture.BLINK to context.getString(R.string.head_control_gesture_blink)
    )
    
    val currentGesture = settings.selectGesture()
    val gestureIndex = availableGestures.indexOf(currentGesture).let { if (it == -1) 0 else it }
    
    var selectedGesture by remember { mutableIntStateOf(gestureIndex) }
    var gestureSelectionEnabled by remember { mutableStateOf(settings.isGestureSelectionEnabled()) }
    
    // Hold time values (in milliseconds)
    val holdTimeValues = longArrayOf(100L, 300L, 500L, 750L, 1000L, 1500L, 2000L)
    val currentHoldTime = settings.gestureHoldTime()
    val holdTimeIndex = holdTimeValues.indexOfFirst { kotlin.math.abs(it - currentHoldTime) < 50L }.let { if (it == -1) 2 else it }
    var gestureHoldTime by remember { mutableIntStateOf(holdTimeIndex) }
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
        
        Section(titleResId = R.string.head_control_gesture_section_title) {
            PreferenceSwitch(
                checked = gestureSelectionEnabled,
                titleResId = R.string.head_control_gesture_enabled_title,
                summaryResId = R.string.head_control_gesture_enabled_summary,
                onCheckedChange = { enabled ->
                    gestureSelectionEnabled = enabled
                    prefs.setBooleanValue(HeadControlSettings.KEY_GESTURE_SELECTION_ENABLED, enabled)
                }
            )
            
            if (gestureSelectionEnabled) {
                PreferenceValueSelector(
                    value = selectedGesture,
                    titleResId = R.string.head_control_gesture_selection_title,
                    summaryResId = R.string.head_control_gesture_selection_summary,
                    values = IntArray(availableGestures.size) { it },
                    buttonLabelFormatter = { gestureNames[availableGestures[it]] ?: "Unknown" },
                    displayFormatter = { gestureNames[availableGestures[it]] ?: "Unknown" },
                    onValueChanged = { index ->
                        selectedGesture = index
                        prefs.setStringValue(HeadControlSettings.KEY_SELECT_GESTURE, availableGestures[index])
                    }
                )
                
                PreferenceValueSelector(
                    value = gestureHoldTime,
                    titleResId = R.string.head_control_gesture_hold_time_title,
                    summaryResId = R.string.head_control_gesture_hold_time_summary,
                    values = IntArray(holdTimeValues.size) { it },
                    buttonLabelFormatter = { "${holdTimeValues[it]}ms" },
                    displayFormatter = { "${holdTimeValues[it]}ms" },
                    onValueChanged = { index ->
                        gestureHoldTime = index
                        prefs.setLongValue(HeadControlSettings.KEY_GESTURE_HOLD_TIME, holdTimeValues[index])
                    }
                )
            }
        }
    }
}