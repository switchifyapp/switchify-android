package com.enaboapps.switchify.screens.pc

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.components.PreferenceComponentBase
import com.enaboapps.switchify.components.PreferenceRowLeadingIcon
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.pc.PcApprovalCodeState
import com.enaboapps.switchify.pc.PcConnectionRowSource
import com.enaboapps.switchify.pc.PcConnectionRowState
import com.enaboapps.switchify.pc.PcConnectionViewModel
import com.enaboapps.switchify.pc.PcConnectionUiState
import com.enaboapps.switchify.pc.PcDiscoveryStatus
import com.enaboapps.switchify.pc.PcRowStatus
import com.enaboapps.switchify.theme.Dimens
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

private val PcConnectionCompactRowWidth = 380.dp
private const val SwitchifyPcDownloadUrl = "https://switchifyapp.com/pc/"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PcConnectionScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: PcConnectionViewModel = viewModel { PcConnectionViewModel(context.applicationContext) }
    val uiState by viewModel.uiState.collectAsState()
    val permissionState = rememberMultiplePermissionsState(pcBluetoothPermissions())
    val permissionGranted = permissionState.permissions.all { it.status.isGranted }
    val shouldShowRationale = permissionState.permissions.any { it.status.shouldShowRationale }
    val hasRuntimePermissions = permissionState.permissions.isNotEmpty()
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    val linkErrorMessage = stringResource(R.string.error_no_app_to_open_link)
    val openDownloadPage = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, SwitchifyPcDownloadUrl.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, linkErrorMessage, Toast.LENGTH_LONG).show()
        }
    }
    val requestBluetoothPermission = {
        if (!hasRequestedPermission || shouldShowRationale) {
            hasRequestedPermission = true
            permissionState.launchMultiplePermissionRequest()
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }
    }

    LaunchedEffect(permissionGranted, hasRuntimePermissions) {
        viewModel.setPermissionRequired(hasRuntimePermissions && !permissionGranted)
        if (!hasRuntimePermissions || permissionGranted) {
            viewModel.startDiscovery()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPcBluetooth()
        }
    }

    BaseView(
        titleResId = R.string.pc_connection_title,
        navController = navController,
        enableScroll = false
    ) {
        ScrollableView {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                PcConnectionStatusPanel(
                    uiState = uiState,
                    onRequestPermission = requestBluetoothPermission
                )
                if (!uiState.permissionRequired) {
                    PcListSection(uiState = uiState, viewModel = viewModel)
                }
                PcDownloadSection(openDownloadPage)
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
private fun PcDownloadSection(onOpenDownload: () -> Unit) {
    Section(titleResId = R.string.pc_connection_setup_section) {
        PreferenceComponentBase(
            titleResId = R.string.pc_connection_download_pc_title,
            summaryResId = R.string.pc_connection_download_pc_summary,
            leadingIcon = Icons.Rounded.Computer,
            onClick = onOpenDownload,
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}

private fun pcBluetoothPermissions(): List<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

@Composable
private fun PcConnectionStatusPanel(
    uiState: PcConnectionUiState,
    onRequestPermission: () -> Unit
) {
    val mode = pcConnectionOverviewMode(uiState)
    val connectedName = connectedPcTitle(uiState)
    val title = when (mode) {
        PcConnectionOverviewMode.PermissionRequired -> stringResource(R.string.pc_connection_status_permission_title)
        PcConnectionOverviewMode.Connected -> stringResource(R.string.pc_connection_status_connected_title, connectedName)
        PcConnectionOverviewMode.Searching -> stringResource(R.string.pc_connection_status_searching_title)
        PcConnectionOverviewMode.Ready -> stringResource(R.string.pc_connection_status_ready_title)
        PcConnectionOverviewMode.Empty -> stringResource(R.string.pc_connection_status_empty_title)
        PcConnectionOverviewMode.Failed -> stringResource(R.string.pc_connection_status_failed_title)
    }
    val summary = when (mode) {
        PcConnectionOverviewMode.PermissionRequired -> stringResource(R.string.pc_connection_status_permission_summary)
        PcConnectionOverviewMode.Connected -> stringResource(R.string.pc_connection_status_connected_summary)
        PcConnectionOverviewMode.Searching -> stringResource(R.string.pc_connection_status_searching_summary)
        PcConnectionOverviewMode.Ready -> stringResource(R.string.pc_connection_status_ready_summary)
        PcConnectionOverviewMode.Empty -> stringResource(R.string.pc_connection_status_empty_summary)
        PcConnectionOverviewMode.Failed -> stringResource(R.string.pc_connection_status_failed_summary)
    }
    val icon = when (mode) {
        PcConnectionOverviewMode.PermissionRequired,
        PcConnectionOverviewMode.Searching -> Icons.Rounded.Bluetooth
        PcConnectionOverviewMode.Connected -> Icons.Rounded.CheckCircle
        PcConnectionOverviewMode.Failed -> Icons.Rounded.Warning
        PcConnectionOverviewMode.Ready,
        PcConnectionOverviewMode.Empty -> Icons.Rounded.Computer
    }

    Panel(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.padding(Dimens.spaceM)) {
            val compact = maxWidth < PcConnectionCompactRowWidth
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                    PcStatusPanelContent(icon = icon, title = title, summary = summary)
                    if (mode == PcConnectionOverviewMode.PermissionRequired) {
                        ActionButton(
                            textResId = R.string.pc_connection_permission_action,
                            applyPadding = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRequestPermission
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PcStatusPanelContent(
                        icon = icon,
                        title = title,
                        summary = summary,
                        modifier = Modifier.weight(1f)
                    )
                    if (mode == PcConnectionOverviewMode.PermissionRequired) {
                        Button(onClick = onRequestPermission) {
                            Text(stringResource(R.string.pc_connection_permission_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PcStatusPanelContent(
    icon: ImageVector,
    title: String,
    summary: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreferenceRowLeadingIcon(imageVector = icon)
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PcListSection(
    uiState: PcConnectionUiState,
    viewModel: PcConnectionViewModel
) {
    Section(titleResId = R.string.pc_connection_pcs_section) {
        Column(modifier = Modifier.padding(vertical = Dimens.spaceS)) {
            if (uiState.pcRows.isEmpty()) {
                PcConnectionDeviceRow(
                    title = stringResource(R.string.pc_connection_nearby_empty_title),
                    summary = stringResource(R.string.pc_connection_nearby_empty_summary),
                    status = null,
                    primaryAction = null,
                    secondaryActions = {},
                    onClick = {}
                )
            } else {
                uiState.pcRows.forEach { row ->
                    PcConnectionDeviceRow(
                        title = row.title,
                        summary = row.summary,
                        status = row.deviceStatus(),
                        primaryAction = row.primaryAction(viewModel),
                        secondaryActions = {
                            PcDefaultAction(
                                isDefault = row.isDefault,
                                canSetDefault = row.canSetDefault,
                                onSetDefault = { viewModel.setDefaultPc(row.desktopId, row.title) }
                            )
                            if (row.canUnpair) {
                                TextButton(onClick = { viewModel.requestUnpair(row.desktopId, row.title) }) {
                                    Text(stringResource(R.string.pc_connection_unpair))
                                }
                            }
                        },
                        onClick = { row.perform(viewModel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PcConnectionDeviceRow(
    title: String,
    summary: String,
    status: PcConnectionDeviceStatus?,
    primaryAction: PcConnectionRowAction?,
    secondaryActions: @Composable RowScope.() -> Unit,
    onClick: () -> Unit
) {
    BoxWithConstraints {
        val compact = maxWidth < PcConnectionCompactRowWidth
        val rowModifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Dimens.spaceM)

        if (compact) {
            Column(
                modifier = rowModifier,
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)
            ) {
                PcDeviceRowText(title = title, summary = summary, status = status)
                PcDeviceActionRow(primaryAction = primaryAction, secondaryActions = secondaryActions)
            }
        } else {
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PcDeviceRowText(
                    title = title,
                    summary = summary,
                    status = status,
                    modifier = Modifier.weight(1f)
                )
                PcDeviceActionRow(
                    primaryAction = primaryAction,
                    secondaryActions = secondaryActions,
                    modifier = Modifier.widthIn(min = 280.dp, max = 420.dp)
                )
            }
        }
    }
}

@Composable
private fun PcDeviceRowText(
    title: String,
    summary: String,
    status: PcConnectionDeviceStatus?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreferenceRowLeadingIcon(imageVector = Icons.Rounded.Computer)
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                status?.let { PcStatusChip(status = it) }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PcDeviceActionRow(
    primaryAction: PcConnectionRowAction?,
    secondaryActions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        horizontalAlignment = Alignment.End
    ) {
        Row(horizontalArrangement = Arrangement.End) {
            primaryAction?.let { action ->
                PcRowActionButton(
                    text = action.label,
                    enabled = action.enabled,
                    emphasized = action.emphasized,
                    onClick = action.onClick
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            secondaryActions()
        }
    }
}

@Composable
private fun PcStatusChip(status: PcConnectionDeviceStatus) {
    val colors = MaterialTheme.colorScheme
    val label = when (status) {
        PcConnectionDeviceStatus.Connected -> stringResource(R.string.pc_connection_status_connected)
        PcConnectionDeviceStatus.Default -> stringResource(R.string.pc_connection_default)
        PcConnectionDeviceStatus.Waiting -> stringResource(R.string.pc_connection_status_waiting)
        PcConnectionDeviceStatus.Connecting -> stringResource(R.string.pc_connection_status_connecting)
        PcConnectionDeviceStatus.Failed -> stringResource(R.string.pc_connection_status_failed)
        PcConnectionDeviceStatus.Saved -> stringResource(R.string.pc_connection_status_saved)
        PcConnectionDeviceStatus.NotAvailable -> stringResource(R.string.pc_connection_status_not_available)
    }
    val containerColor = when (status) {
        PcConnectionDeviceStatus.Connected -> colors.primaryContainer
        PcConnectionDeviceStatus.Default -> colors.secondaryContainer
        PcConnectionDeviceStatus.Failed -> colors.errorContainer
        PcConnectionDeviceStatus.Saved,
        PcConnectionDeviceStatus.Waiting,
        PcConnectionDeviceStatus.Connecting,
        PcConnectionDeviceStatus.NotAvailable -> colors.surfaceVariant
    }
    val contentColor = when (status) {
        PcConnectionDeviceStatus.Connected -> colors.onPrimaryContainer
        PcConnectionDeviceStatus.Default -> colors.onSecondaryContainer
        PcConnectionDeviceStatus.Failed -> colors.onErrorContainer
        PcConnectionDeviceStatus.Saved,
        PcConnectionDeviceStatus.Waiting,
        PcConnectionDeviceStatus.Connecting,
        PcConnectionDeviceStatus.NotAvailable -> colors.onSurfaceVariant
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = Dimens.spaceS, vertical = Dimens.spaceXs)
    )
}

@Composable
private fun PcDefaultAction(
    isDefault: Boolean,
    canSetDefault: Boolean,
    onSetDefault: () -> Unit
) {
    when {
        isDefault -> PcStatusChip(PcConnectionDeviceStatus.Default)
        canSetDefault -> TextButton(onClick = onSetDefault) {
            Text(stringResource(R.string.pc_connection_make_default))
        }
    }
}

private data class PcConnectionRowAction(
    val label: String,
    val enabled: Boolean,
    val emphasized: Boolean,
    val onClick: () -> Unit
)

private enum class PcConnectionDeviceStatus {
    Connected,
    Default,
    Waiting,
    Connecting,
    Failed,
    Saved,
    NotAvailable
}

internal enum class PcConnectionOverviewMode {
    PermissionRequired,
    Connected,
    Searching,
    Ready,
    Empty,
    Failed
}

internal fun pcConnectionOverviewMode(uiState: PcConnectionUiState): PcConnectionOverviewMode {
    return when {
        uiState.permissionRequired -> PcConnectionOverviewMode.PermissionRequired
        uiState.connectedDesktopId != null -> PcConnectionOverviewMode.Connected
        uiState.isDiscovering -> PcConnectionOverviewMode.Searching
        uiState.discoveryStatus == PcDiscoveryStatus.Failed -> PcConnectionOverviewMode.Failed
        uiState.pcRows.isNotEmpty() -> PcConnectionOverviewMode.Ready
        else -> PcConnectionOverviewMode.Empty
    }
}

private fun connectedPcTitle(uiState: PcConnectionUiState): String {
    val desktopId = uiState.connectedDesktopId ?: return "PC"
    return uiState.discoveredPcs.firstOrNull { it.pc.desktopId == desktopId }?.title
        ?: uiState.savedPairings.firstOrNull { it.desktopId == desktopId }?.title
        ?: uiState.pcRows.firstOrNull { it.desktopId == desktopId }?.title
        ?: "PC"
}

private fun PcConnectionRowState.deviceStatus(): PcConnectionDeviceStatus? {
    return when {
        status == PcRowStatus.Connected -> PcConnectionDeviceStatus.Connected
        status == PcRowStatus.WaitingApproval -> PcConnectionDeviceStatus.Waiting
        status == PcRowStatus.Connecting -> PcConnectionDeviceStatus.Connecting
        status == PcRowStatus.Failed -> PcConnectionDeviceStatus.Failed
        isDefault -> PcConnectionDeviceStatus.Default
        source == PcConnectionRowSource.SavedOnly && canConnect -> PcConnectionDeviceStatus.Saved
        source == PcConnectionRowSource.SavedOnly && !canConnect -> PcConnectionDeviceStatus.NotAvailable
        else -> null
    }
}

private fun PcConnectionRowState.primaryAction(viewModel: PcConnectionViewModel): PcConnectionRowAction? {
    return when {
        canRequestAccess && discoveredPc != null -> PcConnectionRowAction(
            label = actionText.orEmpty(),
            enabled = enabled,
            emphasized = true,
            onClick = { viewModel.requestAccess(discoveredPc) }
        )
        canConnect && source == PcConnectionRowSource.Discovered && discoveredPc != null -> PcConnectionRowAction(
            label = actionText.orEmpty(),
            enabled = enabled,
            emphasized = true,
            onClick = { viewModel.connectWithSavedToken(discoveredPc) }
        )
        canConnect && source == PcConnectionRowSource.SavedOnly -> PcConnectionRowAction(
            label = actionText.orEmpty(),
            enabled = enabled,
            emphasized = true,
            onClick = { viewModel.connectSavedPairing(desktopId) }
        )
        else -> null
    }
}

private fun PcConnectionRowState.perform(viewModel: PcConnectionViewModel) {
    primaryAction(viewModel)?.takeIf { it.enabled }?.onClick?.invoke()
        ?: if (canUnpair) viewModel.requestUnpair(desktopId, title) else Unit
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

@Composable
private fun PcRowActionButton(text: String, enabled: Boolean, emphasized: Boolean, onClick: () -> Unit) {
    if (emphasized) {
        Button(onClick = onClick, enabled = enabled) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(text)
        }
    }
}
