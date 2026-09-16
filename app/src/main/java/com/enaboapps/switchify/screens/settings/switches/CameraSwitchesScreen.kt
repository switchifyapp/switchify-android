package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.CameraPermissionHandler
import com.enaboapps.switchify.components.LoadingIndicator
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.components.SwitchAction
import com.enaboapps.switchify.components.SwitchListItem
import com.enaboapps.switchify.components.SwitchType
import com.enaboapps.switchify.screens.settings.switches.models.CameraSwitchesScreenModel
import com.enaboapps.switchify.switches.SwitchEvent
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraSwitchesScreen(navController: NavController, profileId: String? = null) {
    val context = LocalContext.current
    val profileContext = rememberSwitchProfileContext(profileId)
    val targetProfileId = profileContext.targetProfileId
    HandleMissingSwitchProfile(profileContext, navController)
    val cameraSwitchesScreenModel = remember {
        CameraSwitchesScreenModel()
    }
    val uiState by cameraSwitchesScreenModel.uiState.collectAsState()

    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    LaunchedEffect(targetProfileId) {
        targetProfileId?.let { cameraSwitchesScreenModel.setup(context, it) }
    }

    BaseView(
        titleResId = R.string.screen_title_camera_switches,
        navController = navController,
        navBarTrailingContent = {
            SwitchProfileIndicator(profileContext, navController)
        },
        padding = 0.dp,
        enableScroll = false,
        floatingActionButton = {
            if (cameraPermissionState.status.isGranted && targetProfileId != null) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(
                            SwitchProfileRoutes.addCameraSwitch(targetProfileId)
                        )
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_add_24),
                        contentDescription = "Add"
                    )
                }
            }
        }
    ) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            targetProfileId != null -> {
                CameraPermissionHandler(
                    permissionState = cameraPermissionState,
                    onPermissionGranted = {
                        CameraSwitchesContent(
                    cameraSwitches = uiState.cameraSwitches,
                    navController = navController,
                    profileId = targetProfileId
                        )
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

    }
}

@Composable
private fun CameraSwitchesContent(
    cameraSwitches: List<SwitchEvent>,
    navController: NavController,
    profileId: String
) {
    if (cameraSwitches.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No camera switches found",
                style = MaterialTheme.typography.titleMedium
            )
        }
    } else {
        ScrollableView {
            Section(titleResId = R.string.section_title_switches) {
                cameraSwitches.forEach { event ->
                    SwitchEventItem(
                        navController = navController,
                        switchEvent = event,
                        profileId = profileId
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchEventItem(
    navController: NavController,
    switchEvent: SwitchEvent,
    profileId: String
) {
    val gestureName =
        com.enaboapps.switchify.switches.CameraSwitchFacialGesture(switchEvent.code).getName()
    val primaryAction = SwitchAction(
        trigger = gestureName,
        actionName = switchEvent.pressAction.getDisplayName(LocalContext.current)
    )

    SwitchListItem(
        switchName = switchEvent.name,
        switchType = SwitchType.CAMERA,
        primaryAction = primaryAction,
        secondaryActions = emptyList(),
        isEnabled = true,
        hasConfigurationIssues = false,
        onClick = {
            navController.navigate(
                SwitchProfileRoutes.editCameraSwitch(profileId, switchEvent.code)
            )
        }
    )
}
