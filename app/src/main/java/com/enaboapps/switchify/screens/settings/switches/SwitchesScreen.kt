package com.enaboapps.switchify.screens.settings.switches

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.*
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.screens.settings.switches.models.SwitchesScreenModel
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
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
        title = "Switches",
        navController = navController,
        navBarActions = listOf(
            NavBarAction(
                text = "Test Switches",
                onClick = {
                    navController.navigate(NavigationRoute.TestSwitches.name)
                }
            )
        ),
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
                        remoteSwitches = uiState.remoteSwitches,
                        navController = navController,
                        switchesScreenModel = switchesScreenModel
                    )

                    1 -> CameraPermissionHandler(
                        permissionState = cameraPermissionState,
                        onPermissionGranted = {
                            CameraSwitchesContent(
                                localSwitches = uiState.localSwitches.filter { it.type == SWITCH_EVENT_TYPE_CAMERA },
                                remoteSwitches = uiState.remoteSwitches,
                                navController = navController,
                                switchesScreenModel = switchesScreenModel
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
    remoteSwitches: List<SwitchEventStore.RemoteSwitchInfo>,
    navController: NavController,
    switchesScreenModel: SwitchesScreenModel
) {
    SwitchList(
        switches = localSwitches,
        navController = navController,
        emptyMessage = "No external switches found"
    )

    // Display remote switches that aren't on device
    val availableRemoteSwitches = remoteSwitches.filter {
        !it.isOnDevice && it.type == SWITCH_EVENT_TYPE_EXTERNAL
    }

    if (availableRemoteSwitches.isNotEmpty()) {
        Section(title = "Previously Used Switches") {
            availableRemoteSwitches.forEach { remoteSwitch ->
                RemoteSwitchItem(
                    model = switchesScreenModel,
                    remoteSwitch = remoteSwitch,
                    isImporting = switchesScreenModel.uiState.value.importingSwitch == remoteSwitch.code
                )
            }
        }
    }
}

@Composable
private fun CameraSwitchesContent(
    localSwitches: List<SwitchEvent>,
    remoteSwitches: List<SwitchEventStore.RemoteSwitchInfo>,
    navController: NavController,
    switchesScreenModel: SwitchesScreenModel
) {
    SwitchList(
        switches = localSwitches,
        navController = navController,
        emptyMessage = "No camera switches found"
    )

    // Display remote switches that aren't on device
    val availableRemoteSwitches = remoteSwitches.filter {
        !it.isOnDevice && it.type == SWITCH_EVENT_TYPE_CAMERA
    }

    if (availableRemoteSwitches.isNotEmpty()) {
        Section(title = "Previously Used Switches") {
            availableRemoteSwitches.forEach { remoteSwitch ->
                RemoteSwitchItem(
                    model = switchesScreenModel,
                    remoteSwitch = remoteSwitch,
                    isImporting = switchesScreenModel.uiState.value.importingSwitch == remoteSwitch.code
                )
            }
        }
    }
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
        Section(title = "Switches") {
            switches.forEach { event ->
                SwitchEventItem(
                    navController = navController,
                    switchEvent = event
                )
            }
        }
    }
}

@Composable
private fun RemoteSwitchItem(
    model: SwitchesScreenModel,
    remoteSwitch: SwitchEventStore.RemoteSwitchInfo,
    isImporting: Boolean
) {
    val context = LocalContext.current
    val showDeleteConfirmation = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UICard(
            modifier = Modifier.weight(1f),
            title = remoteSwitch.name,
            description = if (isImporting) "Importing..." else "Tap to add",
            onClick = {
                if (!isImporting) {
                    model.importSwitch(remoteSwitch, context)
                }
            },
            enabled = !isImporting
        )
        if (!isImporting) {
            IconButton(onClick = {
                showDeleteConfirmation.value = true
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Switch",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (showDeleteConfirmation.value) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation.value = false },
                title = { Text("Confirm Deletion") },
                text = { Text("Are you sure you want to delete this switch from your account?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation.value = false
                            model.deleteRemoteSwitch(remoteSwitch, context)
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation.value = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
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
        title = switchEvent.name,
        summary = "Edit this switch",
        navController = navController,
        route = "$route/${switchEvent.code}"
    )
}