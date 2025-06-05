package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.enaboapps.switchify.components.NavRouteLink
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.switches.models.SwitchesScreenModel
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchEvent
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SwitchesScreen(navController: NavController) {
    val context = LocalContext.current
    val switchesScreenModel = remember {
        SwitchesScreenModel()
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val uiState by switchesScreenModel.uiState.collectAsState()

    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    LaunchedEffect(Unit) {
        switchesScreenModel.setup(context)
    }

    BaseView(
        titleResId = R.string.screen_title_switches,
        navController = navController,
        padding = 0.dp,
        enableScroll = false,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!switchesScreenModel.isAnotherSwitchAllowed()) {
                        switchesScreenModel.showProAlert()
                    } else {
                        when (selectedTabIndex) {
                            0 -> navController.navigate(NavigationRoute.AddNewExternalSwitch.name)
                            1 -> navController.navigate(NavigationRoute.AddNewCameraSwitch.name)
                        }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_add_24),
                    contentDescription = "Add"
                )
            }
        }
    ) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("External") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Camera") }
            )
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }

            else -> {
                when (selectedTabIndex) {
                    0 -> ExternalSwitchesContent(
                        localSwitches = uiState.localSwitches.filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL },
                        navController = navController
                    )

                    1 -> CameraPermissionHandler(
                        permissionState = cameraPermissionState,
                        onPermissionGranted = {
                            CameraSwitchesContent(
                                localSwitches = uiState.localSwitches.filter { it.type == SWITCH_EVENT_TYPE_CAMERA },
                                navController = navController
                            )
                        },
                        onNavigateBack = { selectedTabIndex = 0 }
                    )
                }
            }
        }

        if (uiState.showProAlert) {
            AlertDialog(
                onDismissRequest = { switchesScreenModel.hideProAlert() },
                title = { Text("Pro Feature") },
                text = { Text("To add another switch, you need to purchase Switchify Pro.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            switchesScreenModel.hideProAlert()
                            navController.navigate(NavigationRoute.Paywall.name)
                        }
                    ) {
                        Text("Purchase")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { switchesScreenModel.hideProAlert() }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ExternalSwitchesContent(
    localSwitches: List<SwitchEvent>,
    navController: NavController
) {
    SwitchList(
        switches = localSwitches,
        navController = navController,
        emptyMessage = "No external switches found"
    )
}

@Composable
private fun CameraSwitchesContent(
    localSwitches: List<SwitchEvent>,
    navController: NavController
) {
    SwitchList(
        switches = localSwitches,
        navController = navController,
        emptyMessage = "No camera switches found"
    )
}

@Composable
private fun SwitchList(
    switches: List<SwitchEvent>,
    navController: NavController,
    emptyMessage: String
) {
    if (switches.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.titleMedium
            )
        }
    } else {
        ScrollableView {
            Section(titleResId = R.string.section_title_switches) {
                switches.forEach { event ->
                    SwitchEventItem(
                        navController = navController,
                        switchEvent = event
                    )
                }
            }
        }
    }
}


@Composable
private fun SwitchEventItem(
    navController: NavController,
    switchEvent: SwitchEvent
) {
    val route = when (switchEvent.type) {
        SWITCH_EVENT_TYPE_EXTERNAL -> NavigationRoute.EditExternalSwitch.name
        SWITCH_EVENT_TYPE_CAMERA -> NavigationRoute.EditCameraSwitch.name
        else -> return
    }

    NavRouteLink(
        runtimeTitle = switchEvent.name,
        summaryResId = R.string.switch_edit_this_switch,
        navController = navController,
        route = "$route/${switchEvent.code}"
    )
}