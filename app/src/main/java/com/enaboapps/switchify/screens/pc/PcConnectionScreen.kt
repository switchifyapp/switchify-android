package com.enaboapps.switchify.screens.pc

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PanelListRow
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
import com.enaboapps.switchify.pc.PcApprovalCodeState
import com.enaboapps.switchify.pc.PcConnectionViewModel
import com.enaboapps.switchify.pc.PcRowState
import com.enaboapps.switchify.pc.PcRowStatus
import com.enaboapps.switchify.theme.Dimens

@Composable
fun PcConnectionScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: PcConnectionViewModel = viewModel { PcConnectionViewModel(context.applicationContext) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    BaseView(
        titleResId = R.string.pc_connection_title,
        navController = navController,
        enableScroll = false
    ) {
        ScrollableView {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)) {
                Section(titleResId = R.string.pc_connection_nearby_section) {
                    Column(modifier = Modifier.padding(vertical = Dimens.spaceS)) {
                        Text(
                            text = uiState.discoveryStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceS)
                        )
                        uiState.discoveredPcs.forEach { row ->
                            PanelListRow(
                                runtimeTitle = row.title,
                                runtimeSummary = row.summary,
                                onClick = { row.perform(viewModel) },
                                trailing = {
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
                            )
                        }
                    }
                }
                if (uiState.savedPairings.isNotEmpty()) {
                    Section(titleResId = R.string.pc_connection_paired_section) {
                        Column(modifier = Modifier.padding(vertical = Dimens.spaceS)) {
                            uiState.savedPairings.forEach { row ->
                                PanelListRow(
                                    runtimeTitle = row.title,
                                    runtimeSummary = row.summary,
                                    onClick = { viewModel.requestUnpair(row.desktopId, row.title) },
                                    trailing = {
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
