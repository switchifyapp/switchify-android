package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcWindowControlAction

data class PcWindowControlSpec(
    @param:StringRes val labelResId: Int,
    val command: PcControlCommand.WindowControl
)

@Composable
fun PcWindowControlScreen(
    connected: Boolean,
    onCommandSelected: (PcControlCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.pc_window_section_windows),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        pcWindowControlSpecs().chunked(2).forEach { specs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                specs.forEach { spec ->
                    PcScannedCommandTile(
                        labelResId = spec.labelResId,
                        enabled = connected,
                        onClick = { onCommandSelected(spec.command) },
                        modifier = Modifier.weight(1f),
                        minHeightDp = 72,
                        square = false
                    )
                }
                if (specs.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

fun pcWindowControlSpecs(): List<PcWindowControlSpec> {
    return listOf(
        PcWindowControlSpec(
            R.string.pc_window_switch_next,
            PcControlCommand.WindowControl(PcWindowControlAction.SwitchNext)
        ),
        PcWindowControlSpec(
            R.string.pc_window_switch_previous,
            PcControlCommand.WindowControl(PcWindowControlAction.SwitchPrevious)
        ),
        PcWindowControlSpec(
            R.string.pc_window_task_view,
            PcControlCommand.WindowControl(PcWindowControlAction.TaskView)
        ),
        PcWindowControlSpec(
            R.string.pc_window_show_desktop,
            PcControlCommand.WindowControl(PcWindowControlAction.ShowDesktop)
        ),
        PcWindowControlSpec(
            R.string.pc_window_minimize,
            PcControlCommand.WindowControl(PcWindowControlAction.MinimizeFocused)
        ),
        PcWindowControlSpec(
            R.string.pc_window_maximize,
            PcControlCommand.WindowControl(PcWindowControlAction.MaximizeFocused)
        ),
        PcWindowControlSpec(
            R.string.pc_window_close,
            PcControlCommand.WindowControl(PcWindowControlAction.CloseFocused)
        )
    )
}
