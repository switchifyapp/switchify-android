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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.components.CollapsibleSection
import com.enaboapps.switchify.pc.PC_SHORTCUT_LETTER_KEYS
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcKeyboardModifierKey
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
    activeModifiers: Set<PcKeyboardModifierKey>,
    onModifierSelected: (PcKeyboardModifierKey) -> Unit,
    onShortcutLetterSelected: (PcKeyboardShortcutKey) -> Unit,
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
            text = stringResource(R.string.pc_window_section_modifiers),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        PcCompactCommandGrid(
            columns = 4,
            minTileHeightDp = 52,
            cells = pcWindowModifierSpecs().map { spec ->
                PcCompactCommandCell(
                    labelResId = spec.labelResId,
                    enabled = enabled,
                    onClick = { onModifierSelected(spec.key) },
                    selected = activeModifiers.contains(spec.key)
                )
            }
        )
        PcShortcutAlphabetAccordion(
            enabled = enabled,
            activeModifiers = activeModifiers,
            onShortcutLetterSelected = onShortcutLetterSelected
        )
        PcKeyboardNavigationCluster(
            enabled = enabled,
            onKeySelected = { key ->
                onCommandSelected(PcControlCommand.PressKey(key))
            }
        )
    }
}

@Composable
fun PcShortcutAlphabetAccordion(
    enabled: Boolean,
    activeModifiers: Set<PcKeyboardModifierKey>,
    onShortcutLetterSelected: (PcKeyboardShortcutKey) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val orderedModifiers = orderedShortcutModifiers(activeModifiers)
    val modifierLabels = orderedModifiers.map { stringResource(it.labelResId) }.joinToString(" + ")
    val subtitle = if (orderedModifiers.isEmpty()) {
        stringResource(R.string.pc_window_shortcuts_no_modifier)
    } else {
        stringResource(R.string.pc_window_shortcuts_with_modifiers, modifierLabels)
    }
    CollapsibleSection(
        title = stringResource(R.string.pc_window_section_shortcuts),
        subtitle = subtitle,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        enabled = enabled,
        modifier = modifier
    ) {
        PcCompactTextCommandGrid(
            columns = 6,
            minTileHeightDp = 44,
            cells = pcWindowShortcutLetterSpecs().map { key ->
                PcCompactTextCommandCell(
                    label = key.protocolValue,
                    enabled = enabled && orderedModifiers.isNotEmpty(),
                    onClick = { onShortcutLetterSelected(key) }
                )
            }
        )
    }
}

data class PcWindowModifierSpec(
    @param:StringRes val labelResId: Int,
    val key: PcKeyboardModifierKey
)

fun pcWindowModifierSpecs(): List<PcWindowModifierSpec> {
    return listOf(
        PcWindowModifierSpec(R.string.pc_modifier_ctrl, PcKeyboardModifierKey.Ctrl),
        PcWindowModifierSpec(R.string.pc_modifier_alt, PcKeyboardModifierKey.Alt),
        PcWindowModifierSpec(R.string.pc_modifier_shift, PcKeyboardModifierKey.Shift),
        PcWindowModifierSpec(R.string.pc_modifier_start, PcKeyboardModifierKey.Meta)
    )
}

fun orderedShortcutModifiers(activeModifiers: Set<PcKeyboardModifierKey>): List<PcKeyboardModifierKey> {
    return listOf(
        PcKeyboardModifierKey.Ctrl,
        PcKeyboardModifierKey.Alt,
        PcKeyboardModifierKey.Shift,
        PcKeyboardModifierKey.Meta
    ).filter { activeModifiers.contains(it) }
}

fun pcWindowShortcutLetterSpecs(): List<PcKeyboardShortcutKey> {
    return PC_SHORTCUT_LETTER_KEYS
}

fun pcWindowCompactControlSpecs(): List<PcWindowControlSpec?> {
    return pcWindowControlSpecs() + listOf(null)
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
