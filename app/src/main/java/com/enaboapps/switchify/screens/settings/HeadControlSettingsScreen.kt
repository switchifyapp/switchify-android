package com.enaboapps.switchify.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA


@Composable
fun MovementSection(
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
    // Individual direction deadzone values using user-friendly levels
    val currentLeftDeadzone = settings.leftDeadzone()
    val leftDeadzoneIndex = HeadControlSettings.getUserFriendlyThresholdIndex(currentLeftDeadzone)
    var leftDeadzone by remember { mutableIntStateOf(leftDeadzoneIndex) }
    
    val currentRightDeadzone = settings.rightDeadzone()
    val rightDeadzoneIndex = HeadControlSettings.getUserFriendlyThresholdIndex(currentRightDeadzone)
    var rightDeadzone by remember { mutableIntStateOf(rightDeadzoneIndex) }
    
    val currentUpDeadzone = settings.upDeadzone()
    val upDeadzoneIndex = HeadControlSettings.getUserFriendlyThresholdIndex(currentUpDeadzone)
    var upDeadzone by remember { mutableIntStateOf(upDeadzoneIndex) }
    
    val currentDownDeadzone = settings.downDeadzone()
    val downDeadzoneIndex = HeadControlSettings.getUserFriendlyThresholdIndex(currentDownDeadzone)
    var downDeadzone by remember { mutableIntStateOf(downDeadzoneIndex) }
    
    PreferenceValueSelector(
        value = leftDeadzone,
        titleResId = R.string.preference_title_head_control_left_deadzone,
        summaryResId = R.string.preference_summary_head_control_left_deadzone,
        values = IntArray(HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS.size) { it },
        buttonLabelFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        displayFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        onValueChanged = { index ->
            leftDeadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_LEFT_DEADZONE, HeadControlSettings.getThresholdValueFromIndex(index))
        }
    )
    
    PreferenceValueSelector(
        value = rightDeadzone,
        titleResId = R.string.preference_title_head_control_right_deadzone,
        summaryResId = R.string.preference_summary_head_control_right_deadzone,
        values = IntArray(HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS.size) { it },
        buttonLabelFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        displayFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        onValueChanged = { index ->
            rightDeadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_RIGHT_DEADZONE, HeadControlSettings.getThresholdValueFromIndex(index))
        }
    )
    
    PreferenceValueSelector(
        value = upDeadzone,
        titleResId = R.string.preference_title_head_control_up_deadzone,
        summaryResId = R.string.preference_summary_head_control_up_deadzone,
        values = IntArray(HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS.size) { it },
        buttonLabelFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        displayFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        onValueChanged = { index ->
            upDeadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_UP_DEADZONE, HeadControlSettings.getThresholdValueFromIndex(index))
        }
    )
    
    PreferenceValueSelector(
        value = downDeadzone,
        titleResId = R.string.preference_title_head_control_down_deadzone,
        summaryResId = R.string.preference_summary_head_control_down_deadzone,
        values = IntArray(HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS.size) { it },
        buttonLabelFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        displayFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        onValueChanged = { index ->
            downDeadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_DOWN_DEADZONE, HeadControlSettings.getThresholdValueFromIndex(index))
        }
    )
}

@Composable
fun UnifiedDeadzoneControl(
    settings: HeadControlSettings,
    prefs: PreferenceManager
) {
    val currentDeadzone = settings.deadzone()
    val deadzoneIndex = HeadControlSettings.getUserFriendlyThresholdIndex(currentDeadzone)
    var deadzone by remember { mutableIntStateOf(deadzoneIndex) }
    
    PreferenceValueSelector(
        value = deadzone,
        titleResId = R.string.preference_title_head_control_deadzone,
        summaryResId = R.string.preference_summary_head_control_deadzone,
        values = IntArray(HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS.size) { it },
        buttonLabelFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        displayFormatter = { HeadControlSettings.USER_FRIENDLY_THRESHOLD_LEVELS[it] },
        onValueChanged = { index ->
            deadzone = index
            prefs.setFloatValue(HeadControlSettings.KEY_DEADZONE, HeadControlSettings.getThresholdValueFromIndex(index))
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
    
    // Separate directional thresholds state
    var useSeparateThresholds by remember { mutableStateOf(settings.useSeparateDirectionalThresholds()) }
    
    ScrollableView {
        // Movement speed settings
        MovementSection(settings = settings, prefs = prefs)
        
        DeadzoneSection(
            settings = settings, 
            prefs = prefs,
            useSeparateThresholds = useSeparateThresholds,
            onSeparateThresholdsChanged = { enabled ->
                useSeparateThresholds = enabled
                prefs.setBooleanValue(HeadControlSettings.KEY_SEPARATE_DIRECTIONAL_THRESHOLDS, enabled)
            }
        )

        Section(titleResId = R.string.section_title_head_control_menu_navigation) {
            val currentInitial = settings.menuRepeatInitialDelay()
            val initialIndex = HeadControlSettings.MENU_REPEAT_INITIAL_VALUES.indexOfFirst { kotlin.math.abs(it - currentInitial) < 60L }.let { if (it == -1) 2 else it }
            var initialDelay by remember { mutableIntStateOf(initialIndex) }

            PreferenceValueSelector(
                value = initialDelay,
                titleResId = R.string.preference_title_head_control_menu_repeat_initial,
                summaryResId = R.string.preference_summary_head_control_menu_repeat_initial,
                values = IntArray(HeadControlSettings.MENU_REPEAT_INITIAL_VALUES.size) { it },
                buttonLabelFormatter = { "${HeadControlSettings.MENU_REPEAT_INITIAL_VALUES[it]}ms" },
                displayFormatter = { "${HeadControlSettings.MENU_REPEAT_INITIAL_VALUES[it]}ms" },
                onValueChanged = { index ->
                    initialDelay = index
                    prefs.setLongValue(HeadControlSettings.KEY_MENU_REPEAT_INITIAL_DELAY, HeadControlSettings.MENU_REPEAT_INITIAL_VALUES[index])
                }
            )

            val currentInterval = settings.menuRepeatInterval()
            val intervalIndex = HeadControlSettings.MENU_REPEAT_INTERVAL_VALUES.indexOfFirst { kotlin.math.abs(it - currentInterval) < 40L }.let { if (it == -1) 2 else it }
            var interval by remember { mutableIntStateOf(intervalIndex) }

            PreferenceValueSelector(
                value = interval,
                titleResId = R.string.preference_title_head_control_menu_repeat_interval,
                summaryResId = R.string.preference_summary_head_control_menu_repeat_interval,
                values = IntArray(HeadControlSettings.MENU_REPEAT_INTERVAL_VALUES.size) { it },
                buttonLabelFormatter = { "${HeadControlSettings.MENU_REPEAT_INTERVAL_VALUES[it]}ms" },
                displayFormatter = { "${HeadControlSettings.MENU_REPEAT_INTERVAL_VALUES[it]}ms" },
                onValueChanged = { index ->
                    interval = index
                    prefs.setLongValue(HeadControlSettings.KEY_MENU_REPEAT_INTERVAL, HeadControlSettings.MENU_REPEAT_INTERVAL_VALUES[index])
                }
            )
        }
    }
}

@Composable
fun HeadControlSelectionTab(
    settings: HeadControlSettings,
    prefs: PreferenceManager,
    context: android.content.Context
) {
    val availableGestures = com.enaboapps.switchify.service.face.FacialGestureRegistry.switchAssignableIds()
    
    val currentGesture = settings.selectGesture()
    val gestureIndex = availableGestures.indexOf(currentGesture).let { if (it == -1) 0 else it }
    
    var selectedGesture by remember { mutableIntStateOf(gestureIndex) }
    var headControlPriority by remember { mutableStateOf(settings.isHeadControlPriorityEnabled()) }
    
    // Use centralized hold time values
    val currentHoldTime = settings.gestureHoldTime()
    val holdTimeIndex = HeadControlSettings.HOLD_TIME_VALUES.indexOfFirst { kotlin.math.abs(it - currentHoldTime) < 50L }.let { if (it == -1) 2 else it }
    var gestureHoldTime by remember { mutableIntStateOf(holdTimeIndex) }
    var conflictAssigned by remember { mutableStateOf(false) }
    
    ScrollableView {
        Section(titleResId = R.string.head_control_gesture_section_title) {
            PreferenceSwitch(
                checked = headControlPriority,
                titleResId = R.string.head_control_priority_title,
                summaryResId = R.string.head_control_priority_summary,
                onCheckedChange = { enabled ->
                    headControlPriority = enabled
                    prefs.setBooleanValue(HeadControlSettings.KEY_GESTURE_PRIORITY_HEAD_CONTROL, enabled)
                }
            )
            
            PreferenceValueSelector(
                value = selectedGesture,
                titleResId = R.string.head_control_gesture_selection_title,
                summaryResId = R.string.head_control_gesture_selection_summary,
                values = IntArray(availableGestures.size) { it },
                buttonLabelFormatter = { CameraSwitchFacialGesture(availableGestures[it]).getName() },
                displayFormatter = { CameraSwitchFacialGesture(availableGestures[it]).getName() },
                onValueChanged = { index ->
                    selectedGesture = index
                    prefs.setStringValue(HeadControlSettings.KEY_SELECT_GESTURE, availableGestures[index])
                }
            )
            
            val selectedGestureId = availableGestures.getOrNull(selectedGesture) ?: currentGesture
            LaunchedEffect(selectedGestureId) {
                val store = SwitchEventStore.getInstance()
                store.initializeAsync(context)
                conflictAssigned = store.getSwitchEvents().any { it.type == SWITCH_EVENT_TYPE_CAMERA && it.code == selectedGestureId }
            }
            if (conflictAssigned) {
                Text(
                    text = stringResource(R.string.head_control_conflict_warning),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
            
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
