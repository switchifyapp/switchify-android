package com.enaboapps.switchify.screens.pc

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcKeyboardShortcutKey
import com.enaboapps.switchify.pc.PcWindowControlAction

data class PcWindowControlSpec(
    @param:StringRes val labelResId: Int,
    val command: PcControlCommand,
    val icon: ImageVector? = null,
    val tone: PcCommandTone = PcCommandTone.Neutral
)

@Composable
fun PcWindowControlScreen(
    enabled: Boolean,
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
        PcCompactCommandGrid(
            columns = 3,
            minTileHeightDp = 52,
            cells = pcWindowCompactControlSpecs().map { spec ->
                spec?.let {
                    PcCompactCommandCell(
                        labelResId = it.labelResId,
                        enabled = enabled,
                        onClick = { onCommandSelected(it.command) },
                        icon = it.icon,
                        tone = it.tone
                    )
                }
            }
        )
        Text(
            text = stringResource(R.string.pc_window_section_shortcuts),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        PcCompactCommandGrid(
            columns = 3,
            minTileHeightDp = 52,
            cells = pcWindowShortcutSpecs().map { spec ->
                PcCompactCommandCell(
                    labelResId = spec.labelResId,
                    enabled = enabled,
                    onClick = { onCommandSelected(spec.command) },
                    icon = spec.icon,
                    tone = spec.tone
                )
            }
        )
        PcKeyboardNavigationCluster(
            enabled = enabled,
            onKeySelected = { key ->
                onCommandSelected(PcControlCommand.PressKey(key))
            }
        )
    }
}

fun pcWindowCompactControlSpecs(): List<PcWindowControlSpec?> {
    return pcWindowControlSpecs() + listOf(null)
}

fun pcWindowShortcutSpecs(): List<PcWindowControlSpec> {
    return listOf(
        PcWindowControlSpec(
            R.string.pc_shortcut_select_all,
            PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.A))
        ),
        PcWindowControlSpec(
            R.string.pc_shortcut_copy,
            PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.C))
        ),
        PcWindowControlSpec(
            R.string.pc_shortcut_cut,
            PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.X))
        )
    )
}

fun pcWindowControlSpecs(): List<PcWindowControlSpec> {
    return listOf(
        PcWindowControlSpec(
            R.string.pc_key_start,
            PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Meta)),
            Icons.Rounded.Computer
        ),
        PcWindowControlSpec(
            R.string.pc_window_switch_next,
            PcControlCommand.WindowControl(PcWindowControlAction.SwitchNext),
            Icons.AutoMirrored.Filled.ArrowForward
        ),
        PcWindowControlSpec(
            R.string.pc_window_switch_previous,
            PcControlCommand.WindowControl(PcWindowControlAction.SwitchPrevious),
            Icons.AutoMirrored.Filled.ArrowBack
        ),
        PcWindowControlSpec(
            R.string.pc_window_task_view,
            PcControlCommand.WindowControl(PcWindowControlAction.TaskView),
            Icons.Rounded.Computer
        ),
        PcWindowControlSpec(
            R.string.pc_window_show_desktop,
            PcControlCommand.WindowControl(PcWindowControlAction.ShowDesktop),
            Icons.Rounded.Computer
        ),
        PcWindowControlSpec(
            R.string.pc_window_minimize,
            PcControlCommand.WindowControl(PcWindowControlAction.MinimizeFocused),
            Icons.Default.Remove
        ),
        PcWindowControlSpec(
            R.string.pc_window_maximize,
            PcControlCommand.WindowControl(PcWindowControlAction.MaximizeFocused),
            Icons.Default.OpenWith
        ),
        PcWindowControlSpec(
            R.string.pc_window_close,
            PcControlCommand.WindowControl(PcWindowControlAction.CloseFocused),
            Icons.Default.Close,
            PcCommandTone.Destructive
        )
    )
}
