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
import androidx.compose.ui.res.stringResource
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
        titleResId = R.string.screen_title_switches,
        navController = navController,
        navBarActions = listOf(
            NavBarAction(
                textResId = R.string.action_test,
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
        Section(titleResId = R.string.section_title_previously_used_switches) {
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
        Section(titleResId = R.string.section_title_previously_used_switches) {
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
            runtimeTitle = remoteSwitch.name,
            descriptionResId = if (isImporting) R.string.switch_importing else R.string.switch_tap_to_add,
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
                title = { Text(stringResource(R.string.dialog_title_delete)) },
                text = { Text(stringResource(R.string.dialog_message_delete_switch_from_account)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation.value = false
                            model.deleteRemoteSwitch(remoteSwitch, context)
                        }
                    ) {
                        Text(stringResource(R.string.button_delete))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation.value = false
                        }
                    ) {
                        Text(stringResource(R.string.button_cancel))
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
        runtimeTitle = switchEvent.name,
        summaryResId = R.string.switch_edit_this_switch,
        navController = navController,
        route = "$route/${switchEvent.code}"
    )
}