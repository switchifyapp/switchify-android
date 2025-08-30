package com.enaboapps.switchify.screens.settings

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.CameraPermissionHandler
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.screens.settings.models.CameraSettingsScreenModel
import com.enaboapps.switchify.service.face.FaceProcessingService
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val viewModel: CameraSettingsScreenModel = viewModel { CameraSettingsScreenModel(context) }

    BaseView(
        titleResId = R.string.screen_title_camera_settings,
        navController = navController,
        enableScroll = false
    ) {
        CameraPermissionHandler(
            permissionState = cameraPermissionState,
            onPermissionGranted = {
                CameraSettingsContent(viewModel = viewModel, lifecycleOwner = lifecycleOwner)
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun CameraSettingsContent(
    viewModel: CameraSettingsScreenModel,
    lifecycleOwner: LifecycleOwner
) {
    val detectedExpressions by viewModel.detectedExpressions.collectAsState()
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()

    // Collect threshold state flows
    val smileTime by viewModel.smileTime.collectAsState()
    val leftWinkTime by viewModel.leftWinkTime.collectAsState()
    val rightWinkTime by viewModel.rightWinkTime.collectAsState()
    val blinkTime by viewModel.blinkTime.collectAsState()
    val headTurnLeftSensitivity by viewModel.headTurnLeftSensitivity.collectAsState()
    val headTurnRightSensitivity by viewModel.headTurnRightSensitivity.collectAsState()
    val headTurnUpSensitivity by viewModel.headTurnUpSensitivity.collectAsState()
    val headTurnDownSensitivity by viewModel.headTurnDownSensitivity.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.tab_test_gestures),
        stringResource(R.string.tab_timing_settings),
        stringResource(R.string.tab_sensitivity)
    )

    LaunchedEffect(lifecycleOwner) {
        viewModel.startCamera(lifecycleOwner)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanup()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                0 -> TestGesturesTab(
                    detectedExpressions = detectedExpressions,
                    isFaceDetected = isFaceDetected
                )

                1 -> TimingSettingsTab(
                    viewModel = viewModel,
                    smileTime = smileTime,
                    leftWinkTime = leftWinkTime,
                    rightWinkTime = rightWinkTime,
                    blinkTime = blinkTime
                )

                2 -> SensitivityTab(
                    viewModel = viewModel,
                    headTurnLeftSensitivity = headTurnLeftSensitivity,
                    headTurnRightSensitivity = headTurnRightSensitivity,
                    headTurnUpSensitivity = headTurnUpSensitivity,
                    headTurnDownSensitivity = headTurnDownSensitivity
                )
            }
        }

        // Small persistent camera preview in bottom right
        Card(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            CameraPreview(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CameraPreview(
    viewModel: CameraSettingsScreenModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    viewModel.bindPreview(this)
                }
            },
            update = { previewView ->
                // Rebind preview when switching back to this tab
                viewModel.bindPreview(previewView)
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun ExpressionFeedback(
    detectedExpressions: Set<String>,
    isFaceDetected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !isFaceDetected -> MaterialTheme.colorScheme.errorContainer
                detectedExpressions.isNotEmpty() -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !isFaceDetected -> {
                    Text(
                        text = stringResource(R.string.no_face_detected),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                detectedExpressions.isNotEmpty() -> {
                    detectedExpressions.forEach { expression ->
                        val gestureName = CameraSwitchFacialGesture(expression).getName()
                        Text(
                            text = stringResource(R.string.expression_detected, gestureName),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.no_expression_detected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectedExpressionsList(detectedExpressions: Set<String>) {
    if (detectedExpressions.isEmpty()) {
        Text(
            text = "Try making facial expressions to see them detected here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(detectedExpressions.toList()) { expression ->
                val gesture = CameraSwitchFacialGesture(expression)
                ExpressionItem(
                    name = gesture.getName(),
                    description = gesture.getDescription()
                )
            }
        }
    }
}

@Composable
private fun ExpressionItem(
    name: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun TestGesturesTab(
    detectedExpressions: Set<String>,
    isFaceDetected: Boolean
) {
    ScrollableView {
        Section(titleResId = R.string.section_title_expression_testing) {
            ExpressionFeedback(
                detectedExpressions = detectedExpressions,
                isFaceDetected = isFaceDetected
            )
        }

        Section(titleResId = R.string.section_title_detected_expressions) {
            DetectedExpressionsList(detectedExpressions = detectedExpressions)
        }
    }
}

@Composable
private fun TimingSettingsTab(
    viewModel: CameraSettingsScreenModel,
    smileTime: Long,
    leftWinkTime: Long,
    rightWinkTime: Long,
    blinkTime: Long
) {
    ScrollableView {
        Section(titleResId = R.string.section_title_threshold_settings) {
            PreferenceTimeStepper(
                value = smileTime,
                titleResId = R.string.preference_title_smile_time,
                summaryResId = R.string.preference_summary_smile_time,
                min = 100,
                max = 5000,
                step = 100,
                onValueChanged = { viewModel.setSmileTime(it) }
            )

            PreferenceTimeStepper(
                value = leftWinkTime,
                titleResId = R.string.preference_title_left_wink_time,
                summaryResId = R.string.preference_summary_left_wink_time,
                min = 100,
                max = 3000,
                step = 50,
                onValueChanged = { viewModel.setLeftWinkTime(it) }
            )

            PreferenceTimeStepper(
                value = rightWinkTime,
                titleResId = R.string.preference_title_right_wink_time,
                summaryResId = R.string.preference_summary_right_wink_time,
                min = 100,
                max = 3000,
                step = 50,
                onValueChanged = { viewModel.setRightWinkTime(it) }
            )

            PreferenceTimeStepper(
                value = blinkTime,
                titleResId = R.string.preference_title_blink_time,
                summaryResId = R.string.preference_summary_blink_time,
                min = 100,
                max = 2000,
                step = 50,
                onValueChanged = { viewModel.setBlinkTime(it) }
            )
        }
    }
}

@Composable
private fun SensitivityTab(
    viewModel: CameraSettingsScreenModel,
    headTurnLeftSensitivity: Int,
    headTurnRightSensitivity: Int,
    headTurnUpSensitivity: Int,
    headTurnDownSensitivity: Int
) {
    ScrollableView {
        Section(titleResId = R.string.section_title_sensitivity_settings) {
            PreferenceValueSelector(
                value = headTurnLeftSensitivity,
                titleResId = R.string.preference_title_head_turn_left_sensitivity,
                summaryResId = R.string.preference_summary_head_turn_left_sensitivity,
                min = 1,
                max = 10,
                displayFormatter = { sensitivity ->
                    "${FaceProcessingService.getHeadTurnThreshold(sensitivity).toInt()}°"
                },
                onValueChanged = { viewModel.setHeadTurnLeftSensitivity(it) }
            )

            PreferenceValueSelector(
                value = headTurnRightSensitivity,
                titleResId = R.string.preference_title_head_turn_right_sensitivity,
                summaryResId = R.string.preference_summary_head_turn_right_sensitivity,
                min = 1,
                max = 10,
                displayFormatter = { sensitivity ->
                    "${FaceProcessingService.getHeadTurnThreshold(sensitivity).toInt()}°"
                },
                onValueChanged = { viewModel.setHeadTurnRightSensitivity(it) }
            )

            PreferenceValueSelector(
                value = headTurnUpSensitivity,
                titleResId = R.string.preference_title_head_turn_up_sensitivity,
                summaryResId = R.string.preference_summary_head_turn_up_sensitivity,
                min = 1,
                max = 10,
                displayFormatter = { sensitivity ->
                    "${FaceProcessingService.getHeadTurnThreshold(sensitivity).toInt()}°"
                },
                onValueChanged = { viewModel.setHeadTurnUpSensitivity(it) }
            )

            PreferenceValueSelector(
                value = headTurnDownSensitivity,
                titleResId = R.string.preference_title_head_turn_down_sensitivity,
                summaryResId = R.string.preference_summary_head_turn_down_sensitivity,
                min = 1,
                max = 10,
                displayFormatter = { sensitivity ->
                    "${FaceProcessingService.getHeadTurnThreshold(sensitivity).toInt()}°"
                },
                onValueChanged = { viewModel.setHeadTurnDownSensitivity(it) }
            )
        }
    }
}