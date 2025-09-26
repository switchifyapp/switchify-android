package com.enaboapps.switchify.screens.settings

import android.Manifest
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.components.CameraPermissionHandler
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceSwitch
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.screens.settings.models.HeadControlTestTabModel
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
    val currentHoldTime = settings.getSelectGestureHoldTime()
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
                    settings.setSelectGesture(availableGestures[index])
                }
            )
            
            // Menu gesture selector - only show gestures that are different from selection gesture
            val availableMenuGestures = settings.getAvailableMenuGestures()
            val currentMenuGesture = settings.menuGesture()
            val menuGestureIndex = availableMenuGestures.indexOf(currentMenuGesture).let { if (it == -1) 0 else it }
            var selectedMenuGesture by remember { mutableIntStateOf(menuGestureIndex) }
            
            // Update available menu gestures when selection gesture changes
            LaunchedEffect(selectedGesture) {
                val updatedMenuGestures = settings.getAvailableMenuGestures()
                if (updatedMenuGestures.isNotEmpty()) {
                    val newMenuGestureIndex = updatedMenuGestures.indexOf(currentMenuGesture).let { if (it == -1) 0 else it }
                    selectedMenuGesture = newMenuGestureIndex
                    // Update preference if current menu gesture is no longer available
                    if (!updatedMenuGestures.contains(currentMenuGesture)) {
                        settings.setMenuGesture(updatedMenuGestures[0])
                    }
                }
            }
            
            if (availableMenuGestures.isNotEmpty()) {
                PreferenceValueSelector(
                    value = selectedMenuGesture,
                    titleResId = R.string.head_control_menu_gesture_title,
                    summaryResId = R.string.head_control_menu_gesture_summary,
                    values = IntArray(availableMenuGestures.size) { it },
                    buttonLabelFormatter = { CameraSwitchFacialGesture(availableMenuGestures[it]).getName() },
                    displayFormatter = { CameraSwitchFacialGesture(availableMenuGestures[it]).getName() },
                    onValueChanged = { index ->
                        selectedMenuGesture = index
                        settings.setMenuGesture(availableMenuGestures[index])
                    }
                )
            }
            
            // Menu gesture hold time selector
            if (availableMenuGestures.isNotEmpty()) {
                val currentMenuHoldTime = settings.getMenuGestureHoldTime()
                val menuHoldTimeIndex = HeadControlSettings.HOLD_TIME_VALUES.indexOfFirst { kotlin.math.abs(it - currentMenuHoldTime) < 50L }.let { if (it == -1) 3 else it }
                var menuGestureHoldTime by remember { mutableIntStateOf(menuHoldTimeIndex) }
                
                PreferenceValueSelector(
                    value = menuGestureHoldTime,
                    titleResId = R.string.head_control_menu_gesture_hold_time_title,
                    summaryResId = R.string.head_control_menu_gesture_hold_time_summary,
                    values = IntArray(HeadControlSettings.HOLD_TIME_VALUES.size) { it },
                    buttonLabelFormatter = { "${HeadControlSettings.HOLD_TIME_VALUES[it]}ms" },
                    displayFormatter = { "${HeadControlSettings.HOLD_TIME_VALUES[it]}ms" },
                    onValueChanged = { index ->
                        menuGestureHoldTime = index
                        prefs.setLongValue(HeadControlSettings.KEY_MENU_GESTURE_HOLD_TIME, HeadControlSettings.HOLD_TIME_VALUES[index])
                    }
                )
            }
            
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
            
            // Gesture conflict validation
            val validationResult = settings.validateGestureSettings()
            if (validationResult != com.enaboapps.switchify.service.techniques.headcontrol.GestureValidationResult.VALID) {
                val errorMessage = when (validationResult) {
                    com.enaboapps.switchify.service.techniques.headcontrol.GestureValidationResult.DUPLICATE_GESTURES -> 
                        stringResource(R.string.head_control_duplicate_gestures_error)
                    com.enaboapps.switchify.service.techniques.headcontrol.GestureValidationResult.INVALID_SELECT_GESTURE -> 
                        stringResource(R.string.head_control_invalid_select_gesture_error)
                    com.enaboapps.switchify.service.techniques.headcontrol.GestureValidationResult.INVALID_MENU_GESTURE -> 
                        stringResource(R.string.head_control_invalid_menu_gesture_error)
                    else -> ""
                }
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
                
                // Auto-resolve conflicts when detected
                LaunchedEffect(validationResult) {
                    if (validationResult == com.enaboapps.switchify.service.techniques.headcontrol.GestureValidationResult.DUPLICATE_GESTURES) {
                        if (settings.resolveGestureConflicts()) {
                            Toast.makeText(context, context.getString(R.string.head_control_gesture_auto_resolved), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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
                    prefs.setLongValue(HeadControlSettings.KEY_SELECT_GESTURE_HOLD_TIME, HeadControlSettings.HOLD_TIME_VALUES[index])
                }
            )
        }
    }
}

// Helper function to calculate movement (similar to HeadControlManager logic)
private fun calculateTestMovement(headRotationX: Float, headRotationY: Float, settings: HeadControlSettings): Pair<Float, Float> {
    val leftDeadzone = settings.getEffectiveLeftDeadzone()
    val rightDeadzone = settings.getEffectiveRightDeadzone()
    val upDeadzone = settings.getEffectiveUpDeadzone()
    val downDeadzone = settings.getEffectiveDownDeadzone()
    val movementSpeed = settings.movementSpeed()
    val headRotationRange = 30f // Same as HeadControlManager.HEAD_ROTATION_RANGE
    val movementDelta = 2f // Scaled down from HeadControlManager.MOVEMENT_DELTA

    // Calculate horizontal movement
    val horizontalMovement = if (headRotationY > 0 && headRotationY > rightDeadzone) {
        val normalizedRotation = (headRotationY - rightDeadzone) / (headRotationRange - rightDeadzone)
        normalizedRotation.coerceIn(0f, 1f) * movementSpeed
    } else if (headRotationY < 0 && abs(headRotationY) > leftDeadzone) {
        val normalizedRotation = (abs(headRotationY) - leftDeadzone) / (headRotationRange - leftDeadzone)
        -(normalizedRotation.coerceIn(0f, 1f) * movementSpeed)
    } else 0f

    // Calculate vertical movement
    val verticalMovement = if (headRotationX > 0 && headRotationX > downDeadzone) {
        val normalizedRotation = (headRotationX - downDeadzone) / (headRotationRange - downDeadzone)
        normalizedRotation.coerceIn(0f, 1f) * movementSpeed
    } else if (headRotationX < 0 && abs(headRotationX) > upDeadzone) {
        val normalizedRotation = (abs(headRotationX) - upDeadzone) / (headRotationRange - upDeadzone)
        -(normalizedRotation.coerceIn(0f, 1f) * movementSpeed)
    } else 0f

    return Pair(horizontalMovement * movementDelta, verticalMovement * movementDelta)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HeadControlTestTab(
    settings: HeadControlSettings,
    context: android.content.Context
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    CameraPermissionHandler(
        permissionState = cameraPermissionState,
        onPermissionGranted = {
            HeadControlTestContent(settings = settings, context = context)
        },
        onNavigateBack = { /* No navigation back needed in test tab */ }
    )
}

@Composable
private fun HeadControlTestContent(
    settings: HeadControlSettings,
    context: android.content.Context
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val viewModel: HeadControlTestTabModel = viewModel {
        HeadControlTestTabModel(context.applicationContext as android.app.Application)
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Screen dimensions
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    // Convert to pixels for calculations
    val screenWidthPx = with(density) { screenWidthDp.toPx() }
    val screenHeightPx = with(density) { screenHeightDp.toPx() }

    // Ball state
    var ballX by remember { mutableFloatStateOf(screenWidthPx / 2f) }
    var ballY by remember { mutableFloatStateOf(screenHeightPx / 2f) }
    val ballRadius = with(density) { 24.dp.toPx() }

    // Collect head pose data from view model
    val headRotationX by viewModel.headRotationX.collectAsState()
    val headRotationY by viewModel.headRotationY.collectAsState()
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()

    // Coordinate system info state
    var coordinateSystemInfo by remember { mutableStateOf("Loading...") }
    var showCoordinateInfo by remember { mutableStateOf(false) }

    // Update ball position based on real head pose data
    LaunchedEffect(headRotationX, headRotationY) {
        if (isFaceDetected) {
            // Calculate movement based on current settings
            val movement = calculateTestMovement(headRotationX, headRotationY, settings)

            // Apply movement with bounds checking
            ballX = (ballX + movement.first).coerceIn(ballRadius, screenWidthPx - ballRadius)
            ballY = (ballY + movement.second).coerceIn(ballRadius, screenHeightPx - ballRadius)
        }
    }

    // Reset function
    val resetBall = {
        ballX = screenWidthPx / 2f
        ballY = screenHeightPx / 2f
        viewModel.resetHeadPose()
    }

    // Apply movement based on head pose (using current settings)
    val movementSpeed = settings.movementSpeed()
    val leftDeadzone = settings.getEffectiveLeftDeadzone()
    val rightDeadzone = settings.getEffectiveRightDeadzone()
    val upDeadzone = settings.getEffectiveUpDeadzone()
    val downDeadzone = settings.getEffectiveDownDeadzone()

    ScrollableView {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title and instructions
            Text(
                text = stringResource(R.string.head_control_test_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.head_control_test_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Camera preview with overlay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Camera preview
                    AndroidView(
                        factory = { context ->
                            PreviewView(context).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                viewModel.setupCamera(this, lifecycleOwner)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Ball overlay
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val cardWidth = size.width
                        val cardHeight = size.height

                        // Scale ball position to card dimensions
                        val normalizedX = (ballX / screenWidthPx).coerceIn(0f, 1f)
                        val normalizedY = (ballY / screenHeightPx).coerceIn(0f, 1f)

                        val ballDrawX = normalizedX * cardWidth
                        val ballDrawY = normalizedY * cardHeight

                        // Draw the ball
                        drawCircle(
                            color = Color(0xFF2196F3), // Blue color
                            radius = ballRadius * 0.3f, // Scale down for the card
                            center = Offset(ballDrawX, ballDrawY)
                        )
                    }

                    // Face detection status
                    if (!isFaceDetected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Position your face in the camera",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current head pose values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = stringResource(R.string.head_control_test_horizontal, headRotationY),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.head_control_test_vertical, headRotationX),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Face detection status
            Text(
                text = if (isFaceDetected) "Face detected ✓" else "No face detected",
                style = MaterialTheme.typography.bodySmall,
                color = if (isFaceDetected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reset button
            Button(
                onClick = resetBall,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.head_control_test_reset))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current settings display
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Current Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Movement Speed: ${String.format(Locale.US, "%.1f", movementSpeed)}")
                    if (settings.useSeparateDirectionalThresholds()) {
                        Text("Left Deadzone: ${String.format(Locale.US, "%.1f°", leftDeadzone)}")
                        Text("Right Deadzone: ${String.format(Locale.US, "%.1f°", rightDeadzone)}")
                        Text("Up Deadzone: ${String.format(Locale.US, "%.1f°", upDeadzone)}")
                        Text("Down Deadzone: ${String.format(Locale.US, "%.1f°", downDeadzone)}")
                    } else {
                        Text("Deadzone: ${String.format(Locale.US, "%.1f°", settings.deadzone())}")
                    }
                }
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
        stringResource(R.string.tab_head_control_selection),
        stringResource(R.string.tab_head_control_test)
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
                2 -> HeadControlTestTab(settings = settings, context = context)
            }
        }
    }
}
