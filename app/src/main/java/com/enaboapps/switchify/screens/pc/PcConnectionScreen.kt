package com.enaboapps.switchify.screens.pc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.PanelListRow
import com.enaboapps.switchify.components.ScrollableView
import com.enaboapps.switchify.components.Section
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
                                    PcRowActionButton(
                                        text = row.actionText,
                                        enabled = row.enabled,
                                        connected = row.status == PcRowStatus.Connected,
                                        onClick = { row.perform(viewModel) }
                                    )
                                }
                            )
                        }
                    }
                }

                Section(titleResId = R.string.pc_connection_fallback_section) {
                    PanelListRow(
                        titleResId = R.string.pc_connection_qr_fallback_title,
                        summaryResId = R.string.pc_connection_qr_fallback_summary,
                        onClick = viewModel::showQrFallbackMessage
                    )
                }
            }
        }
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
