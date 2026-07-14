package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcDisplayDirection
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcKeyboardModifierKey
import com.enaboapps.switchify.pc.PcKeyboardShortcutKey
import com.enaboapps.switchify.pc.PcWindowControlAction
import com.enaboapps.switchify.pc.toShortcutKey
import org.junit.Assert.assertEquals
import org.junit.Test

class PcWindowControlScreenTest {
    @Test
    fun monitorCommandsUseDirectionalCrossOrder() {
        val specs = pcDisplayNavigationSpecs()

        assertEquals(9, specs.size)
        assertEquals(
            listOf(
                PcControlCommand.MoveToDisplay(PcDisplayDirection.Up),
                PcControlCommand.MoveToDisplay(PcDisplayDirection.Left),
                PcControlCommand.MoveToDisplay(PcDisplayDirection.Right),
                PcControlCommand.MoveToDisplay(PcDisplayDirection.Down)
            ),
            specs.mapNotNull { it?.command }
        )
        assertEquals(listOf(0, 2, 4, 6, 8), specs.indices.filter { specs[it] == null })
    }

    @Test
    fun monitorVisibilityAndEnabledStateAreCapabilitySafe() {
        assertEquals(false, shouldShowPcDisplayNavigation(supported = false, displayCount = 2))
        assertEquals(false, shouldShowPcDisplayNavigation(supported = true, displayCount = 1))
        assertEquals(true, shouldShowPcDisplayNavigation(supported = true, displayCount = 2))
        assertEquals(false, pcDisplayNavigationControlsEnabled(surfaceEnabled = false, isDragging = false))
        assertEquals(false, pcDisplayNavigationControlsEnabled(surfaceEnabled = true, isDragging = true))
        assertEquals(true, pcDisplayNavigationControlsEnabled(surfaceEnabled = true, isDragging = false))
    }

    @Test
    fun windowCommandsUseStableOrder() {
        val commands = pcWindowControlSpecs().map { it.command }

        assertEquals(
            listOf(
                PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Meta)),
                PcControlCommand.WindowControl(PcWindowControlAction.SwitchNext),
                PcControlCommand.WindowControl(PcWindowControlAction.SwitchPrevious),
                PcControlCommand.WindowControl(PcWindowControlAction.TaskView),
                PcControlCommand.WindowControl(PcWindowControlAction.ShowDesktop),
                PcControlCommand.WindowControl(PcWindowControlAction.MinimizeFocused),
                PcControlCommand.WindowControl(PcWindowControlAction.MaximizeFocused),
                PcControlCommand.WindowControl(PcWindowControlAction.CloseFocused)
            ),
            commands
        )
    }

    @Test
    fun windowCommandLabelsMatchActions() {
        val specs = pcWindowControlSpecs()

        assertEquals(R.string.pc_key_start, specs[0].labelResId)
        assertEquals(PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Meta)), specs[0].command)
        assertEquals(R.string.pc_window_switch_next, specs[1].labelResId)
        assertEquals(PcControlCommand.WindowControl(PcWindowControlAction.SwitchNext), specs[1].command)
        assertEquals(R.string.pc_window_switch_previous, specs[2].labelResId)
        assertEquals(PcControlCommand.WindowControl(PcWindowControlAction.SwitchPrevious), specs[2].command)
        assertEquals(R.string.pc_window_task_view, specs[3].labelResId)
        assertEquals(PcControlCommand.WindowControl(PcWindowControlAction.TaskView), specs[3].command)
        assertEquals(R.string.pc_window_show_desktop, specs[4].labelResId)
        assertEquals(PcControlCommand.WindowControl(PcWindowControlAction.ShowDesktop), specs[4].command)
        assertEquals(R.string.pc_window_minimize, specs[5].labelResId)
        assertEquals(PcControlCommand.WindowControl(PcWindowControlAction.MinimizeFocused), specs[5].command)
        assertEquals(R.string.pc_window_maximize, specs[6].labelResId)
        assertEquals(PcControlCommand.WindowControl(PcWindowControlAction.MaximizeFocused), specs[6].command)
        assertEquals(R.string.pc_window_close, specs[7].labelResId)
        assertEquals(PcControlCommand.WindowControl(PcWindowControlAction.CloseFocused), specs[7].command)
    }

    @Test
    fun compactWindowCommandsKeepOrderAndPadFinalRow() {
        val specs = pcWindowCompactControlSpecs()
        val commands = specs.mapNotNull { it?.command }

        assertEquals(9, specs.size)
        assertEquals(8, specs.filterNotNull().size)
        assertEquals(pcWindowControlSpecs().map { it.command }, commands)
        assertEquals(null, specs[8])
    }

    @Test
    fun shortcutLetterSpecsUseAlphabeticalOrder() {
        assertEquals(
            ('A'..'Z').map { it.toString() },
            pcWindowShortcutLetterSpecs().map { it.protocolValue }
        )
    }

    @Test
    fun modifierSpecsUseStableOrder() {
        val specs = pcWindowModifierSpecs()

        assertEquals(
            listOf(
                PcKeyboardModifierKey.Ctrl,
                PcKeyboardModifierKey.Alt,
                PcKeyboardModifierKey.Shift,
                PcKeyboardModifierKey.Meta
            ),
            specs.map { it.key }
        )
        assertEquals(
            listOf(
                R.string.pc_modifier_ctrl,
                R.string.pc_modifier_alt,
                R.string.pc_modifier_shift,
                R.string.pc_modifier_start
            ),
            specs.map { it.labelResId }
        )
    }

    @Test
    fun shortcutModifiersUseStableOrderAndMapping() {
        val modifiers = orderedShortcutModifiers(
            setOf(
                PcKeyboardModifierKey.Meta,
                PcKeyboardModifierKey.Shift,
                PcKeyboardModifierKey.Ctrl,
                PcKeyboardModifierKey.Alt
            )
        )

        assertEquals(
            listOf(
                PcKeyboardModifierKey.Ctrl,
                PcKeyboardModifierKey.Alt,
                PcKeyboardModifierKey.Shift,
                PcKeyboardModifierKey.Meta
            ),
            modifiers
        )
        assertEquals(
            listOf(
                PcKeyboardShortcutKey.Ctrl,
                PcKeyboardShortcutKey.Alt,
                PcKeyboardShortcutKey.Shift,
                PcKeyboardShortcutKey.Meta
            ),
            modifiers.map { it.toShortcutKey() }
        )
    }

    @Test
    fun closeWindowCommandUsesDestructiveTone() {
        val specs = pcWindowControlSpecs()

        assertEquals(PcCommandTone.Destructive, specs[7].tone)
    }

    @Test
    fun windowKeyboardNavigationUsesSharedOrder() {
        assertEquals(
            listOf(
                PcKeyboardKey.Escape,
                PcKeyboardKey.ArrowUp,
                PcKeyboardKey.Enter,
                PcKeyboardKey.ArrowLeft,
                PcKeyboardKey.ArrowDown,
                PcKeyboardKey.ArrowRight
            ),
            pcKeyboardNavigationKeys()
        )
    }
}
