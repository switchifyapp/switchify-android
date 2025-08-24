package com.enaboapps.switchify.screens.settings

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.CameraPermissionHandler
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.service.switches.camera.CameraSwitchManager
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import androidx.camera.view.PreviewView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enaboapps.switchify.screens.settings.models.CameraSettingsScreenModel

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
    
    LaunchedEffect(lifecycleOwner) {
        viewModel.startCamera(lifecycleOwner)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanup()
        }
    }

    ScrollableView {
        Section(titleResId = R.string.section_title_expression_testing) {
            CameraPreview(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ExpressionFeedback(
                detectedExpressions = detectedExpressions,
                isFaceDetected = isFaceDetected
            )
        }
        
        Section(titleResId = R.string.section_title_detected_expressions) {
            DetectedExpressionsList(detectedExpressions = detectedExpressions)
        }
        
        Section(titleResId = R.string.section_title_camera_settings) {
            Text(
                text = stringResource(R.string.placeholder_future_settings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
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