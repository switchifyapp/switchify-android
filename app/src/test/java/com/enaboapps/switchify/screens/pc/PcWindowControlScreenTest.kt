package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcKeyboardShortcutKey
import com.enaboapps.switchify.pc.PcWindowControlAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PcWindowControlScreenTest {
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
    fun windowShortcutCommandsUseStableOrder() {
        val commands = pcWindowShortcutSpecs().map { it.command }

        assertEquals(
            listOf(
                PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.A)),
                PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.C)),
                PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.X))
            ),
            commands
        )
    }

    @Test
    fun windowShortcutLabelsMatchActions() {
        val specs = pcWindowShortcutSpecs()

        assertEquals(R.string.pc_shortcut_select_all, specs[0].labelResId)
        assertEquals(
            PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.A)),
            specs[0].command
        )
        assertEquals(R.string.pc_shortcut_copy, specs[1].labelResId)
        assertEquals(
            PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.C)),
            specs[1].command
        )
        assertEquals(R.string.pc_shortcut_cut, specs[2].labelResId)
        assertEquals(
            PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Ctrl, PcKeyboardShortcutKey.X)),
            specs[2].command
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
