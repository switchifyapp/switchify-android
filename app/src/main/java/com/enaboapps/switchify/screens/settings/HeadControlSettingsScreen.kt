package com.enaboapps.switchify.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import java.util.Locale
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture

@Composable
fun AbsoluteModeSection(
    settings: HeadControlSettings,
    prefs: PreferenceManager
) {
    // Use centralized values from HeadControlSettings
    val currentSensitivity = settings.sensitivity()
    val sensitivityIndex = HeadControlSettings.SENSITIVITY_VALUES.indexOfFirst { kotlin.math.abs(it - currentSensitivity) < 0.05f }.let { if (it == -1) 3 else it }
    var sensitivity by remember { mutableIntStateOf(sensitivityIndex) }
    
    Section(titleResId = R.string.section_title_sensitivity) {
        PreferenceValueSelector(
            value = sensitivity,
            titleResId = R.string.preference_title_head_control_sensitivity,
            summaryResId = R.string.preference_summary_head_control_sensitivity,
            values = IntArray(HeadControlSettings.SENSITIVITY_VALUES.size) { it },
            buttonLabelFormatter = { String.format(Locale.US, "%.1f", HeadControlSettings.SENSITIVITY_VALUES[it]) },
            displayFormatter = { String.format(Locale.US, "%.1f", HeadControlSettings.SENSITIVITY_VALUES[it]) },
            onValueChanged = { index ->
                sensitivity = index
                prefs.setFloatValue(HeadControlSettings.KEY_SENSITIVITY, HeadControlSettings.SENSITIVITY_VALUES[index])
            }
        )
    }
}

@Composable  
fun ContinuousModeSection(
    settings: HeadControlSettings,
    prefs: PreferenceManager
) {
    // Use centralized movement speed values
    val currentMovementSpeed = settings.movementSpeed()
    val movementSpeedIndex = HeadControlSettings.MOVEMENT_SPEED_VALUES.indexOfFirst { kotlin.math.abs(it - currentMovementSpeed) < 0.1f }.let { if (it == -1) 5 else it }
    var movementSpeed by remember { mutableIntStateOf(movementSpeedIndex) }
    
    Section(titleResId = R.string.section_title_movement_speed) {
        PreferenceValueSelector(
            value = movementSpeed,
            titleResId = R.string.preference_title_head_control_movement_speed,
            summaryResId = R.string.preference_summary_head_control_movement_speed,
            values = IntArray(HeadControlSettings.MOVEMENT_SPEED_VALUES.size) { it },
            buttonLabelFormatter = { String.format(Locale.US, "%.1f", HeadControlSettings.MOVEMENT_SPEED_VALUES[it]) },
            displayFormatter = { String.format(Locale.US, "%.1f", HeadControlSettings.MOVEMENT_SPEED_VALUES[it]) },
            onValueChanged = { index ->
                movementSpeed = index
                prefs.setFloatValue(HeadControlSettings.KEY_MOVEMENT_SPEED, HeadControlSettings.MOVEMENT_SPEED_VALUES[index])
            }
        )
    }
}

@Composable
fun DirectionalDeadzoneControls(
    settings: HeadControlSettings,
    prefs: PreferenceManager
) {
    // Individual direction deadzone values
    val currentLeftDeadzone = settings.leftDeadzone()
    val leftDeadzoneIndex = HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES.indexOfFirst { kotlin.math.abs(it - currentLeftDeadzone) < 0.05f }.takeIf { it != -1 } ?: 9
    var leftDeadzone by remember { mutableIntStateOf(leftDeadzoneIndex) }
    
    val currentRightDeadzone = settings.rightDeadzone()
    val rightDeadzoneIndex = HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES.indexOfFirst { kotlin.math.abs(it - currentRightDeadzone) < 0.05f }.takeIf { it != -1 } ?: 9
    var rightDeadzone by remember { mutableIntStateOf(rightDeadzoneIndex) }
    
    val currentUpDeadzone = settings.upDeadzone()
    val upDeadzoneIndex = HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES.indexOfFirst { kotlin.math.abs(it - currentUpDeadzone) < 0.05f }.takeIf { it != -1 } ?: 9
    var upDeadzone by remember { mutableIntStateOf(upDeadzoneIndex) }
    
    val currentDownDeadzone = settings.downDeadzone()
    val downDeadzoneIndex = HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES.indexOfFirst { kotlin.math.abs(it - currentDownDeadzone) < 0.05f }.takeIf { it != -1 } ?: 9
    var downDeadzone by remember { mutableIntStateOf(downDeadzoneIndex) }
    
    PreferenceValueSelector(
        value = leftDeadzone,
        titleResId = R.string.preference_title_head_control_left_deadzone,
        summaryResId = R.string.preference_summary_head_control_left_deadzone,
        values = IntArray(HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES.size) { it },
        buttonLabelFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[it]) },
        displayFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[it]) },
        onValueChanged = { index ->
            leftDeadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_LEFT_DEADZONE, HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[index])
        }
    )
    
    PreferenceValueSelector(
        value = rightDeadzone,
        titleResId = R.string.preference_title_head_control_right_deadzone,
        summaryResId = R.string.preference_summary_head_control_right_deadzone,
        values = IntArray(HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES.size) { it },
        buttonLabelFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[it]) },
        displayFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[it]) },
        onValueChanged = { index ->
            rightDeadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_RIGHT_DEADZONE, HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[index])
        }
    )
    
    PreferenceValueSelector(
        value = upDeadzone,
        titleResId = R.string.preference_title_head_control_up_deadzone,
        summaryResId = R.string.preference_summary_head_control_up_deadzone,
        values = IntArray(HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES.size) { it },
        buttonLabelFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[it]) },
        displayFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[it]) },
        onValueChanged = { index ->
            upDeadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_UP_DEADZONE, HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[index])
        }
    )
    
    PreferenceValueSelector(
        value = downDeadzone,
        titleResId = R.string.preference_title_head_control_down_deadzone,
        summaryResId = R.string.preference_summary_head_control_down_deadzone,
        values = IntArray(HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES.size) { it },
        buttonLabelFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[it]) },
        displayFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[it]) },
        onValueChanged = { index ->
            downDeadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_DOWN_DEADZONE, HeadControlSettings.DIRECTIONAL_DEADZONE_VALUES[index])
        }
    )
}

@Composable
fun UnifiedDeadzoneControl(
    settings: HeadControlSettings,
    prefs: PreferenceManager
) {
    val currentDeadzone = settings.deadzone()
    val deadzoneIndex = HeadControlSettings.DEADZONE_VALUES.indexOfFirst { kotlin.math.abs(it - currentDeadzone) < 0.05f }.let { if (it == -1) 2 else it }
    var deadzone by remember { mutableIntStateOf(deadzoneIndex) }
    
    PreferenceValueSelector(
        value = deadzone,
        titleResId = R.string.preference_title_head_control_deadzone,
        summaryResId = R.string.preference_summary_head_control_deadzone,
        values = IntArray(HeadControlSettings.DEADZONE_VALUES.size) { it },
        buttonLabelFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DEADZONE_VALUES[it]) },
        displayFormatter = { String.format(Locale.US, "%.1f°", HeadControlSettings.DEADZONE_VALUES[it]) },
        onValueChanged = { index ->
            deadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_DEADZONE, HeadControlSettings.DEADZONE_VALUES[index])
        }
    )
}

@Composable
fun DeadzoneSection(
    settings: HeadControlSettings,
    prefs: PreferenceManager,
    useSeparateThresholds: Boolean,
    onSeparateThresholdsChanged: (Boolean) -> Unit
) {
    Section(titleResId = R.string.section_title_deadzone_settings) {
        PreferenceSwitch(
            checked = useSeparateThresholds,
            titleResId = R.string.preference_title_head_control_separate_thresholds,
            summaryResId = R.string.preference_summary_head_control_separate_thresholds,
            onCheckedChange = onSeparateThresholdsChanged
        )

        if (useSeparateThresholds) {
            DirectionalDeadzoneControls(settings = settings, prefs = prefs)
        } else {
            UnifiedDeadzoneControl(settings = settings, prefs = prefs)
        }
    }
}

@Composable
fun HeadControlMovementTab(
    settings: HeadControlSettings,
    prefs: PreferenceManager
) {
    val context = LocalContext.current
    
    // Movement mode state
    var isAbsoluteMode by remember { mutableStateOf(settings.isAbsoluteMode()) }
    
    // Separate directional thresholds state
    var useSeparateThresholds by remember { mutableStateOf(settings.useSeparateDirectionalThresholds()) }
    
    ScrollableView {
        Section(titleResId = R.string.section_title_movement_mode) {
            PreferenceValueSelector(
                value = if (isAbsoluteMode) 0 else 1,
                titleResId = R.string.preference_title_head_control_movement_mode,
                summaryResId = R.string.preference_summary_head_control_movement_mode,
                values = intArrayOf(0, 1),
                buttonLabelFormatter = { if (it == 0) context.getString(R.string.head_control_absolute_mode) else context.getString(R.string.head_control_continuous_mode) },
                displayFormatter = { if (it == 0) context.getString(R.string.head_control_absolute_mode) else context.getString(R.string.head_control_continuous_mode) },
                onValueChanged = { index ->
                    isAbsoluteMode = (index == 0)
                    prefs.setBooleanValue(HeadControlSettings.KEY_ABSOLUTE_MODE, isAbsoluteMode)
                }
            )
        }
        
        if (isAbsoluteMode) {
            AbsoluteModeSection(settings = settings, prefs = prefs)
        } else {
            ContinuousModeSection(settings = settings, prefs = prefs)
        }
        
        DeadzoneSection(
            settings = settings, 
            prefs = prefs,
            useSeparateThresholds = useSeparateThresholds,
            onSeparateThresholdsChanged = { enabled ->
                useSeparateThresholds = enabled
                prefs.setBooleanValue(HeadControlSettings.KEY_SEPARATE_DIRECTIONAL_THRESHOLDS, enabled)
            }
        )
    }
}

@Composable
fun HeadControlSelectionTab(
    settings: HeadControlSettings,
    prefs: PreferenceManager,
    context: android.content.Context
) {
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
    
    // Use centralized hold time values
    val currentHoldTime = settings.gestureHoldTime()
    val holdTimeIndex = HeadControlSettings.HOLD_TIME_VALUES.indexOfFirst { kotlin.math.abs(it - currentHoldTime) < 50L }.let { if (it == -1) 2 else it }
    var gestureHoldTime by remember { mutableIntStateOf(holdTimeIndex) }
    
    ScrollableView {
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
                    values = IntArray(HeadControlSettings.HOLD_TIME_VALUES.size) { it },
                    buttonLabelFormatter = { "${HeadControlSettings.HOLD_TIME_VALUES[it]}ms" },
                    displayFormatter = { "${HeadControlSettings.HOLD_TIME_VALUES[it]}ms" },
                    onValueChanged = { index ->
                        gestureHoldTime = index
                        prefs.setLongValue(HeadControlSettings.KEY_GESTURE_HOLD_TIME, HeadControlSettings.HOLD_TIME_VALUES[index])
                    }
                )
            }
        }
    }
}

@Composable
fun HeadControlSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = PreferenceManager(context)
    val settings = HeadControlSettings(context)

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.tab_head_control_movement),
        stringResource(R.string.tab_head_control_selection)
    )
    
    BaseView(
        titleResId = R.string.screen_title_head_control_settings,
        navController = navController,
        enableScroll = false
    ) {
        Column {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> HeadControlMovementTab(settings = settings, prefs = prefs)
                1 -> HeadControlSelectionTab(settings = settings, prefs = prefs, context = context)
            }
        }
    }
}