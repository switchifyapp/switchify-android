package com.enaboapps.switchify.screens.settings

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.CameraPermissionHandler
import com.enaboapps.switchify.components.NavigationHintCard
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.PreferenceValueSelector
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
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
                CameraSettingsContent(
                    viewModel = viewModel, 
                    lifecycleOwner = lifecycleOwner,
                    navController = navController
                )
            },
            onNavigateBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun CameraSettingsContent(
    viewModel: CameraSettingsScreenModel,
    lifecycleOwner: LifecycleOwner,
    navController: NavController
) {
    val detectedExpressions by viewModel.detectedExpressions.collectAsState()
    val isFaceDetected by viewModel.isFaceDetected.collectAsState()

    // Collect threshold state flows
    val smileTime by viewModel.smileTime.collectAsState()
    val leftWinkTime by viewModel.leftWinkTime.collectAsState()
    val rightWinkTime by viewModel.rightWinkTime.collectAsState()
    val blinkTime by viewModel.blinkTime.collectAsState()
    val mouthOpenTime by viewModel.mouthOpenTime.collectAsState()
    
    // Collect real-time blendshape scores
    val smileScore by viewModel.smileScore.collectAsState()
    val leftWinkScore by viewModel.leftWinkScore.collectAsState()
    val rightWinkScore by viewModel.rightWinkScore.collectAsState()
    val blinkScore by viewModel.blinkScore.collectAsState()
    val mouthOpenScore by viewModel.mouthOpenScore.collectAsState()

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
            // Navigation hint to Head Control Settings
            NavigationHintCard(
                titleResId = R.string.camera_settings_navigation_hint_title,
                descriptionResId = R.string.camera_settings_navigation_hint_description,
                onNavigate = {
                    navController.navigate(NavigationRoute.HeadControlSettings.name)
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
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
                    isFaceDetected = isFaceDetected,
                    smileScore = smileScore,
                    leftWinkScore = leftWinkScore,
                    rightWinkScore = rightWinkScore,
                    blinkScore = blinkScore,
                    mouthOpenScore = mouthOpenScore
                )

                1 -> TimingSettingsTab(
                    viewModel = viewModel,
                    smileTime = smileTime,
                    leftWinkTime = leftWinkTime,
                    rightWinkTime = rightWinkTime,
                    blinkTime = blinkTime,
                    mouthOpenTime = mouthOpenTime
                )

                2 -> SensitivityTab(
                    viewModel = viewModel
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
private fun ExpressionProgressBars(
    smileScore: Float,
    leftWinkScore: Float,
    rightWinkScore: Float,
    blinkScore: Float,
    mouthOpenScore: Float
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressionProgressItem(
            name = stringResource(R.string.head_control_gesture_smile),
            score = smileScore,
            threshold = 0.35f
        )
        ExpressionProgressItem(
            name = stringResource(R.string.head_control_gesture_left_wink),
            score = leftWinkScore,
            threshold = 0.55f
        )
        ExpressionProgressItem(
            name = stringResource(R.string.head_control_gesture_right_wink),
            score = rightWinkScore,
            threshold = 0.55f
        )
        ExpressionProgressItem(
            name = stringResource(R.string.head_control_gesture_blink),
            score = blinkScore,
            threshold = 0.55f
        )
        ExpressionProgressItem(
            name = stringResource(R.string.head_control_gesture_mouth_open),
            score = mouthOpenScore,
            threshold = 0.7f
        )
    }
}

@Composable
private fun ExpressionProgressItem(
    name: String,
    score: Float,
    threshold: Float
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = String.format("%.2f", score),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            // Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(score.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        if (score >= threshold) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary
                    )
            )
            
            // Threshold indicator line
            Box(
                modifier = Modifier
                    .fillMaxWidth(threshold.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    .width(2.dp)
                    .align(Alignment.CenterEnd)
            )
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
    isFaceDetected: Boolean,
    smileScore: Float,
    leftWinkScore: Float,
    rightWinkScore: Float,
    blinkScore: Float,
    mouthOpenScore: Float
) {
    ScrollableView {
        Section(titleResId = R.string.section_title_expression_testing) {
            ExpressionFeedback(
                detectedExpressions = detectedExpressions,
                isFaceDetected = isFaceDetected
            )
        }

        Section(titleResId = R.string.section_title_expression_levels) {
            ExpressionProgressBars(
                smileScore = smileScore,
                leftWinkScore = leftWinkScore,
                rightWinkScore = rightWinkScore,
                blinkScore = blinkScore,
                mouthOpenScore = mouthOpenScore
            )
        }
    }
}

@Composable
private fun TimingSettingsTab(
    viewModel: CameraSettingsScreenModel,
    smileTime: Long,
    leftWinkTime: Long,
    rightWinkTime: Long,
    blinkTime: Long,
    mouthOpenTime: Long
) {
    ScrollableView {
        Section(titleResId = R.string.section_title_camera_switch_timing) {
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

            PreferenceTimeStepper(
                value = mouthOpenTime,
                titleResId = R.string.preference_title_mouth_open_time,
                summaryResId = R.string.preference_summary_mouth_open_time,
                min = 100,
                max = 3000,
                step = 50,
                onValueChanged = { viewModel.setMouthOpenTime(it) }
            )
        }
    }
}

@Composable
private fun SensitivityTab(
    viewModel: CameraSettingsScreenModel
) {
    ScrollableView {
        Section(titleResId = R.string.section_title_sensitivity_settings) {
            Text(
                text = "Head turn gestures have been moved to Head Control settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}