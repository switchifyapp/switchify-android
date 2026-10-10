package com.enaboapps.switchify.screens.pc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.ActionButton
import com.enaboapps.switchify.components.ActionButtonType
import com.enaboapps.switchify.components.BaseView
import com.enaboapps.switchify.components.Panel
import com.enaboapps.switchify.pc.connection.PcConnectionGraph
import com.enaboapps.switchify.pc.connection.PcDiagnosticEntry
import com.enaboapps.switchify.pc.connection.PcDiagnosticLog
import com.enaboapps.switchify.theme.Dimens
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.util.Date

class PcDiagnosticsViewModel(private val log: PcDiagnosticLog) : ViewModel() {
    val entries: StateFlow<List<PcDiagnosticEntry>> = log.entries

    fun export(): String = log.export()

    fun clear() = log.clear()
}

@Composable
fun PcDiagnosticsScreen(navController: NavController) {
    val context = LocalContext.current
    val graph = remember { PcConnectionGraph.getInstance(context) }
    val viewModel: PcDiagnosticsViewModel = viewModel { PcDiagnosticsViewModel(graph.diagnostics) }
    val entries by viewModel.entries.collectAsState()
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    val copiedMessage = stringResource(R.string.pc_diagnostics_copied)
    val exportTitle = stringResource(R.string.pc_diagnostics_export_title)

    BaseView(titleResId = R.string.screen_title_pc_diagnostics, navController = navController) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM)
        ) {
            Text(
                text = stringResource(R.string.pc_diagnostics_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)
            ) {
                ActionButton(
                    textResId = R.string.pc_diagnostics_copy,
                    enabled = entries.isNotEmpty(),
                    applyPadding = false,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(exportTitle, viewModel.export()))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }
                )
                ActionButton(
                    textResId = R.string.pc_diagnostics_export,
                    type = ActionButtonType.SECONDARY,
                    enabled = entries.isNotEmpty(),
                    applyPadding = false,
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, viewModel.export())
                        context.startActivity(Intent.createChooser(send, exportTitle))
                    }
                )
                ActionButton(
                    textResId = R.string.pc_diagnostics_clear,
                    type = ActionButtonType.DESTRUCTIVE,
                    enabled = entries.isNotEmpty(),
                    applyPadding = false,
                    onClick = viewModel::clear
                )
            }
            Panel(modifier = Modifier.fillMaxWidth()) {
                if (entries.isEmpty()) {
                    Column(modifier = Modifier.padding(Dimens.spaceM).semantics(mergeDescendants = true) {}) {
                        Text(
                            text = stringResource(R.string.pc_diagnostics_empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.pc_diagnostics_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column {
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider()
                            Column(modifier = Modifier.padding(Dimens.spaceM).semantics(mergeDescendants = true) {}) {
                                Text(
                                    text = timeFormat.format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(text = entry.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
