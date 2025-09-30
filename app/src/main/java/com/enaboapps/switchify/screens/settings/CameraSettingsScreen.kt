package com.enaboapps.switchify.screens.settings

import android.Manifest
import android.app.Application
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.enaboapps.switchify.components.NavigationHintCard
import com.enaboapps.switchify.components.PreferenceTimeStepper
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.models.CameraSettingsScreenModel
import com.enaboapps.switchify.service.face.FacialGestureRegistry
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val viewModel: CameraSettingsScreenModel =
        viewModel { CameraSettingsScreenModel(context.applicationContext as Application) }

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
    val puckerTime by viewModel.puckerTime.collectAsState()


    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.tab_test_gestures),
        stringResource(R.string.tab_timing_settings)
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
                    onAdjustTiming = { selectedTabIndex = 1 }
                )

                1 -> TimingSettingsTab(
                    viewModel = viewModel,
                    smileTime = smileTime,
                    leftWinkTime = leftWinkTime,
                    rightWinkTime = rightWinkTime,
                    blinkTime = blinkTime,
                    puckerTime = puckerTime
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
    isFaceDetected: Boolean
) {
    if (!isFaceDetected) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.no_face_detected),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


@Composable
private fun TestGesturesTab(
    detectedExpressions: Set<String>,
    isFaceDetected: Boolean,
    onAdjustTiming: () -> Unit
) {
    ScrollableView {
        Section(titleResId = R.string.section_title_expression_testing) {
            ExpressionFeedback(
                isFaceDetected = isFaceDetected
            )

            GestureQuickCheckGrid(detectedExpressions = detectedExpressions)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onAdjustTiming) {
                    Text(text = stringResource(R.string.tab_timing_settings))
                }
            }
        }
    }
}

@Composable
private fun GestureQuickCheckGrid(
    detectedExpressions: Set<String>
) {
    val gestures = FacialGestureRegistry.switchAssignableIds()

    val rows = gestures.chunked(3)

    Column(modifier = Modifier.padding(12.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { id ->
                    val active = detectedExpressions.contains(id)
                    val name = CameraSwitchFacialGesture(id).getName()

                    ElevatedCard(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                repeat(3 - rowItems.size) {
                    Box(modifier = Modifier.weight(1f)) {}
                }
            }
        }

        Text(
            text = stringResource(R.string.onboarding_head_control_requirements),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TimingSettingsTab(
    viewModel: CameraSettingsScreenModel,
    smileTime: Long,
    leftWinkTime: Long,
    rightWinkTime: Long,
    blinkTime: Long,
    puckerTime: Long
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
                value = puckerTime,
                titleResId = R.string.preference_title_pucker_time,
                summaryResId = R.string.preference_summary_pucker_time,
                min = 100,
                max = 3000,
                step = 100,
                onValueChanged = { viewModel.setPuckerTime(it) }
            )

        }
    }
}

 
