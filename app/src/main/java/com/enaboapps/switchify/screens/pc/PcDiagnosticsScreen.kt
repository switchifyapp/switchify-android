package com.enaboapps.switchify.screens.pc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

class PcDiagnosticsViewModel(private val log: PcDiagnosticLog) : ViewModel() {
    val entries: StateFlow<List<PcDiagnosticEntry>> = log.entries

    fun export(): String = log.export()

    fun clear() = log.clear()

    companion object {
        const val EXPORT_MIME_TYPE = "text/plain"

        private val FILE_TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

        fun exportFileName(nowMillis: Long): String =
            "switchify-pc-diagnostics-${FILE_TIMESTAMP.format(Instant.ofEpochMilli(nowMillis))}.txt"
    }
}

@Composable
fun PcDiagnosticsScreen(navController: NavController) {
    val context = LocalContext.current
    val graph = remember { PcConnectionGraph.getInstance(context) }
    val viewModel: PcDiagnosticsViewModel = viewModel { PcDiagnosticsViewModel(graph.diagnostics) }
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.MEDIUM) }
    val copiedMessage = stringResource(R.string.pc_diagnostics_copied)
    val exportTitle = stringResource(R.string.pc_diagnostics_export_title)
    val exportedMessage = stringResource(R.string.pc_diagnostics_exported)
    val exportFailedMessage = stringResource(R.string.pc_diagnostics_export_failed)
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PcDiagnosticsViewModel.EXPORT_MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = viewModel.export()
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
                } catch (_: Exception) {
                    false
                }
            }
            Toast.makeText(context, if (written) exportedMessage else exportFailedMessage, Toast.LENGTH_SHORT).show()
        }
    }

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
                    onClick = { exportLauncher.launch(PcDiagnosticsViewModel.exportFileName(System.currentTimeMillis())) }
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
                                Text(text = entry.displayMessage(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
