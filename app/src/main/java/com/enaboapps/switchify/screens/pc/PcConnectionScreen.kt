package com.enaboapps.switchify.screens.pc

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.PanelListRow
import com.enaboapps.switchify.nav.NavigationRoute
import com.enaboapps.switchify.pc.connection.PcConnectionFailure
import com.enaboapps.switchify.pc.connection.PcConnectionGraph
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcListAction
import com.enaboapps.switchify.pc.connection.PcListItem
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.transport.PcBluetoothPermissions
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PcConnectionScreen(navController: NavController) {
    val context = LocalContext.current
    val graph = remember { PcConnectionGraph.getInstance(context) }
    val viewModel: PcConnectionViewModel = viewModel { PcConnectionViewModel(graph.manager, graph.permissions) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pcs by viewModel.pcs.collectAsStateWithLifecycle()
    val defaultDesktopId by viewModel.defaultDesktopId.collectAsStateWithLifecycle()
    val permissionRequested by viewModel.permissionRequested.collectAsStateWithLifecycle()
    val operationFailed by viewModel.operationFailed.collectAsStateWithLifecycle()
    var unpairTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionPromptShown by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionPromptShown = false
        viewModel.onPermissionResult(PcBluetoothPermissions.allGranted(results))
    }

    LaunchedEffect(permissionRequested) {
        if (permissionRequested && !permissionPromptShown) {
            permissionPromptShown = true
            permissionLauncher.launch(PcBluetoothPermissions.required().toTypedArray())
        } else if (!permissionRequested) {
            permissionPromptShown = false
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopScan() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    BaseView(titleResId = R.string.screen_title_pcs, navController = navController) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
        ) {
            Text(
                text = stringResource(R.string.pc_screen_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ConnectionStatus(state = state, onDisconnect = viewModel::disconnect)

            if (operationFailed) {
                MessagePanel(
                    titleResId = R.string.pc_operation_failed_title,
                    bodyResId = R.string.pc_operation_failed_body
                ) {
                    ActionButton(
                        textResId = R.string.pc_dismiss,
                        type = ActionButtonType.SECONDARY,
                        onClick = viewModel::dismissFailure,
                        applyPadding = false
                    )
                }
            }

            val searching = state is PcConnectionState.Scanning
            if (state.activeDesktop == null) {
                ActionButton(
                    textResId = if (searching) R.string.pc_searching else R.string.pc_find_nearby,
                    onClick = viewModel::scan,
                    enabled = !searching,
                    leadingIcon = Icons.Rounded.Bluetooth,
                    applyPadding = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (val current = state) {
                is PcConnectionState.PermissionDenied -> MessagePanel(
                    titleResId = R.string.pc_permission_title,
                    bodyResId = R.string.pc_permission_body
                ) {
                    ActionButton(
                        textResId = R.string.pc_open_settings,
                        type = ActionButtonType.SECONDARY,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    "package:${context.packageName}".toUri()
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        applyPadding = false
                    )
                }
                is PcConnectionState.BluetoothOff -> MessagePanel(
                    titleResId = R.string.pc_bluetooth_off_title,
                    bodyResId = R.string.pc_bluetooth_off_body
                )
                is PcConnectionState.Unsupported -> MessagePanel(
                    titleResId = R.string.pc_unsupported_title,
                    bodyResId = R.string.pc_unsupported_body
                )
                is PcConnectionState.LocationOff -> MessagePanel(
                    titleResId = R.string.pc_location_off_title,
                    bodyResId = R.string.pc_location_off_body
                ) {
                    ActionButton(
                        textResId = R.string.pc_open_location_settings,
                        type = ActionButtonType.SECONDARY,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        applyPadding = false
                    )
                }
                is PcConnectionState.Scanning -> if (current.discovered.isEmpty()) {
                    MessagePanel(titleResId = R.string.pc_looking_title, bodyResId = R.string.pc_looking_body)
                }
                else -> Unit
            }

            if (pcs.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.pc_list_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                pcs.forEachIndexed { index, pc ->
                    PcCard(
                        pc = pc,
                        preferred = pc.saved != null && index == 0,
                        isDefault = pc.saved != null && pc.desktopId == defaultDesktopId,
                        isActive = pc.desktopId == state.activeDesktop?.desktopId,
                        onConnect = { viewModel.connect(pc) },
                        onToggleDefault = {
                            viewModel.setDefault(if (pc.desktopId == defaultDesktopId) null else pc.desktopId)
                        },
                        onUnpair = { unpairTarget = pc.desktopId }
                    )
                }
            }

            Panel(modifier = Modifier.fillMaxWidth()) {
                PanelListRow(
                    titleResId = R.string.screen_title_pc_diagnostics,
                    summaryResId = R.string.pc_diagnostics_summary,
                    leadingIcon = Icons.Rounded.History,
                    onClick = { navController.navigate(NavigationRoute.PcDiagnostics.name) }
                )
            }
        }
    }

    (state as? PcConnectionState.Pairing)?.let { pairing ->
        PairingDialog(
            displayName = pairing.desktop.displayName,
            verificationCode = pairing.verificationCode,
            onCancel = viewModel::disconnect
        )
    }

    val target = pcs.firstOrNull { it.desktopId == unpairTarget && it.saved != null }
    if (target != null) {
        AlertDialog(
            onDismissRequest = { unpairTarget = null },
            title = { Text(stringResource(R.string.pc_unpair_dialog_title, target.desktop.displayName)) },
            text = { Text(stringResource(R.string.pc_unpair_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    unpairTarget = null
                    viewModel.unpair(target.desktopId)
                }) {
                    Text(stringResource(R.string.pc_unpair))
                }
            },
            dismissButton = {
                TextButton(onClick = { unpairTarget = null }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }
}

@Composable
private fun ConnectionStatus(state: PcConnectionState, onDisconnect: () -> Unit) {
    when (state) {
        is PcConnectionState.Connected -> StatusPanel(
            badge = stringResource(R.string.pc_status_connected),
            title = state.desktop.displayName,
            body = stringResource(R.string.pc_connected_to, state.desktop.displayName)
        ) {
            ActionButton(
                textResId = R.string.pc_disconnect,
                type = ActionButtonType.SECONDARY,
                onClick = onDisconnect,
                leadingIcon = Icons.Rounded.LinkOff,
                applyPadding = false
            )
        }
        is PcConnectionState.Connecting -> StatusPanel(
            badge = stringResource(R.string.pc_status_connecting),
            title = state.desktop.displayName,
            body = stringResource(R.string.pc_connecting_to, state.desktop.displayName)
        )
        is PcConnectionState.Reconnecting -> StatusPanel(
            badge = stringResource(R.string.pc_reconnect_attempt, state.attempt),
            title = state.desktop.displayName,
            body = stringResource(R.string.pc_reconnecting_to, state.desktop.displayName)
        )
        is PcConnectionState.Failed -> StatusPanel(
            badge = stringResource(R.string.pc_connection_failed),
            title = stringResource(R.string.pc_could_not_connect),
            body = stringResource(failureMessage(state.failure)),
            error = true
        )
        else -> Unit
    }
}

@Composable
private fun StatusPanel(
    badge: String,
    title: String,
    body: String,
    error: Boolean = false,
    action: @Composable () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    Panel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (error) scheme.errorContainer else scheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Badge(text = badge)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (error) scheme.onErrorContainer else scheme.onPrimaryContainer
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error) scheme.onErrorContainer else scheme.onPrimaryContainer
                )
            }
            action()
        }
    }
}

@Composable
private fun MessagePanel(titleResId: Int, bodyResId: Int, action: @Composable () -> Unit = {}) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
        ) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(text = stringResource(titleResId), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(bodyResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            action()
        }
    }
}

@Composable
private fun PcCard(
    pc: PcListItem,
    preferred: Boolean,
    isDefault: Boolean,
    isActive: Boolean,
    onConnect: () -> Unit,
    onToggleDefault: () -> Unit,
    onUnpair: () -> Unit
) {
    val name = pc.desktop.displayName
    val platform = stringResource(
        when (pc.desktop.platform) {
            PcPlatform.Windows -> R.string.pc_platform_windows
            PcPlatform.MacOs -> R.string.pc_platform_macos
            null -> R.string.pc_platform_unknown
        }
    )
    val status = stringResource(
        when {
            pc.saved != null && pc.nearby -> R.string.pc_status_saved_nearby
            pc.saved != null -> R.string.pc_status_saved
            else -> R.string.pc_status_new
        }
    )
    Panel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceM),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.pc_platform_status, platform, status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isDefault) Badge(text = stringResource(R.string.pc_default_badge))
                    else if (preferred) Badge(text = stringResource(R.string.pc_preferred))
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
            ) {
                if (!isActive) {
                    val connectDescription = stringResource(
                        if (pc.action == PcListAction.Connect) R.string.pc_action_connect_description
                        else R.string.pc_action_request_access_description,
                        name
                    )
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.semantics { contentDescription = connectDescription }
                    ) {
                        Text(
                            stringResource(
                                if (pc.action == PcListAction.Connect) R.string.pc_action_connect
                                else R.string.pc_action_request_access
                            )
                        )
                    }
                }
                if (pc.saved != null) {
                    val defaultDescription = stringResource(
                        if (isDefault) R.string.pc_remove_default_description else R.string.pc_set_default_description,
                        name
                    )
                    FilledTonalButton(
                        onClick = onToggleDefault,
                        modifier = Modifier.semantics { contentDescription = defaultDescription }
                    ) {
                        Text(stringResource(if (isDefault) R.string.pc_remove_default else R.string.pc_set_default))
                    }
                    val unpairDescription = stringResource(R.string.pc_unpair_description, name)
                    TextButton(
                        onClick = onUnpair,
                        modifier = Modifier.semantics { contentDescription = unpairDescription }
                    ) {
                        Text(stringResource(R.string.pc_unpair))
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Dimens.spaceXs, vertical = 4.dp)
        )
    }
}

@Composable
private fun PairingDialog(displayName: String, verificationCode: String, onCancel: () -> Unit) {
    val spokenCode = stringResource(R.string.pc_pairing_code_description, verificationCode.toList().joinToString(" "))
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.pc_pairing_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
                Badge(text = stringResource(R.string.pc_pairing_badge))
                Text(text = displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = verificationCode,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = spokenCode
                        liveRegion = LiveRegionMode.Polite
                    }
                )
                Text(
                    text = stringResource(R.string.pc_pairing_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

private fun failureMessage(failure: PcConnectionFailure): Int = when (failure) {
    PcConnectionFailure.CouldNotConnect -> R.string.pc_failure_could_not_connect
    PcConnectionFailure.NotFoundNearby -> R.string.pc_failure_not_found_nearby
    PcConnectionFailure.SavedAccessLoadFailed -> R.string.pc_failure_saved_access_load_failed
    PcConnectionFailure.SavedAccessUnavailable -> R.string.pc_failure_saved_access_unavailable
    PcConnectionFailure.SavedAccessInvalid -> R.string.pc_failure_saved_access_invalid
    PcConnectionFailure.AccessRevoked -> R.string.pc_failure_access_revoked
    PcConnectionFailure.PairingRejected -> R.string.pc_failure_pairing_rejected
    PcConnectionFailure.PairingExpired -> R.string.pc_failure_pairing_expired
    PcConnectionFailure.ConnectionLost -> R.string.pc_failure_connection_lost
    PcConnectionFailure.DiscoveryFailed -> R.string.pc_failure_discovery_failed
    PcConnectionFailure.UnpairFailed -> R.string.pc_failure_unpair_failed
}
