package com.enaboapps.switchify.screens.pc

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PreferenceComponentBase
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.pc.PcApprovalCodeState
import com.enaboapps.switchify.pc.PcConnectionViewModel
import com.enaboapps.switchify.pc.PcRowState
import com.enaboapps.switchify.pc.PcRowStatus
import com.enaboapps.switchify.theme.Dimens
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

private val PcConnectionCompactRowWidth = 380.dp

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PcConnectionScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: PcConnectionViewModel = viewModel { PcConnectionViewModel(context.applicationContext) }
    val uiState by viewModel.uiState.collectAsState()

    val permissionState: PermissionState? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        null
    }
    val permissionGranted = permissionState?.status?.isGranted ?: true
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(permissionGranted) {
        viewModel.setPermissionRequired(!permissionGranted)
        if (permissionGranted) {
            viewModel.startDiscovery()
        }
    }

    BaseView(
        titleResId = R.string.pc_connection_title,
        navController = navController,
        enableScroll = false
    ) {
        ScrollableView {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                if (uiState.permissionRequired) {
                    Section(titleResId = R.string.pc_connection_permission_section) {
                        Column(modifier = Modifier.padding(vertical = Dimens.spaceS)) {
                            Text(
                                text = stringResource(R.string.pc_connection_permission_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS)
                            )
                            ActionButton(
                                textResId = R.string.pc_connection_permission_action,
                                applyPadding = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS),
                                onClick = {
                                    val state = permissionState ?: return@ActionButton
                                    if (!hasRequestedPermission || state.status.shouldShowRationale) {
                                        hasRequestedPermission = true
                                        state.launchPermissionRequest()
                                    } else {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Section(titleResId = R.string.pc_connection_nearby_section) {
                        Column(modifier = Modifier.padding(vertical = Dimens.spaceS)) {
                            Text(
                                text = uiState.discoveryStatusText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS)
                            )
                            uiState.discoveredPcs.forEach { row ->
                                PcConnectionPreferenceRow(
                                    title = row.title,
                                    summary = row.summary,
                                    onClick = { row.perform(viewModel) },
                                    actions = {
                                        PcNearbyRowActions(row = row, viewModel = viewModel)
                                    }
                                )
                            }
                        }
                    }
                }
                if (uiState.savedPairings.isNotEmpty()) {
                    Section(titleResId = R.string.pc_connection_paired_section) {
                        Column(modifier = Modifier.padding(vertical = Dimens.spaceS)) {
                            uiState.savedPairings.forEach { row ->
                                PcConnectionPreferenceRow(
                                    title = row.title,
                                    summary = row.summary,
                                    onClick = { viewModel.requestUnpair(row.desktopId, row.title) },
                                    actions = {
                                        TextButton(
                                            enabled = row.canUnpair,
                                            onClick = { viewModel.requestUnpair(row.desktopId, row.title) }
                                        ) {
                                            Text(stringResource(R.string.pc_connection_unpair))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.approvalCode?.let { approvalCode ->
        PcApprovalCodeDialog(approvalCode)
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.pc_connection_message_title)) },
            text = { Text(message) }
        )
    }

    uiState.pendingUnpair?.let { pendingUnpair ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUnpair,
            confirmButton = {
                TextButton(onClick = viewModel::confirmUnpair) {
                    Text(stringResource(R.string.pc_connection_unpair))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissUnpair) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.pc_connection_unpair_title)) },
            text = {
                Text(stringResource(R.string.pc_connection_unpair_message, pendingUnpair.displayName))
            }
        )
    }
}

@Composable
private fun PcConnectionPreferenceRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    actions: @Composable () -> Unit
) {
    BoxWithConstraints {
        val compact = maxWidth < PcConnectionCompactRowWidth

        PreferenceComponentBase(
            runtimeTitle = title,
            runtimeSummary = summary,
            onClick = onClick,
            trailing = {
                if (!compact) {
                    actions()
                }
            },
            belowContent = if (compact) {
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        actions()
                    }
                }
            } else {
                null
            }
        )
    }
}

@Composable
private fun PcNearbyRowActions(
    row: PcRowState,
    viewModel: PcConnectionViewModel
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        PcRowActionButton(
            text = row.actionText,
            enabled = row.enabled,
            connected = row.status == PcRowStatus.Connected,
            onClick = { row.perform(viewModel) }
        )
        if (row.canUnpair) {
            TextButton(onClick = { viewModel.requestUnpair(row.pc.desktopId, row.title) }) {
                Text(stringResource(R.string.pc_connection_unpair))
            }
        }
    }
}

@Composable
private fun PcApprovalCodeDialog(approvalCode: PcApprovalCodeState) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(stringResource(R.string.pc_pairing_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                Text(
                    text = stringResource(R.string.pc_pairing_code_pc_name, approvalCode.pcName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.pc_pairing_code_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = approvalCode.verificationCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Dimens.spaceS),
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.pc_pairing_code_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

private fun PcRowState.perform(viewModel: PcConnectionViewModel) {
    when (actionText) {
        "Connect" -> viewModel.connectWithSavedToken(pc)
        "Request access" -> viewModel.requestAccess(pc)
    }
}

@Composable
private fun PcRowActionButton(text: String, enabled: Boolean, connected: Boolean, onClick: () -> Unit) {
    if (connected) {
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(text)
        }
    } else {
        Button(onClick = onClick, enabled = enabled) {
            Text(text)
        }
    }
}
