package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcWindowControlAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PcWindowControlScreenTest {
    @Test
    fun windowCommandsUseStableOrder() {
        val commands = pcWindowControlSpecs().map { it.command }

        assertEquals(
            listOf(
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

        assertEquals(R.string.pc_window_switch_next, specs[0].labelResId)
        assertEquals(PcWindowControlAction.SwitchNext, specs[0].command.action)
        assertEquals(R.string.pc_window_switch_previous, specs[1].labelResId)
        assertEquals(PcWindowControlAction.SwitchPrevious, specs[1].command.action)
        assertEquals(R.string.pc_window_task_view, specs[2].labelResId)
        assertEquals(PcWindowControlAction.TaskView, specs[2].command.action)
        assertEquals(R.string.pc_window_show_desktop, specs[3].labelResId)
        assertEquals(PcWindowControlAction.ShowDesktop, specs[3].command.action)
        assertEquals(R.string.pc_window_minimize, specs[4].labelResId)
        assertEquals(PcWindowControlAction.MinimizeFocused, specs[4].command.action)
        assertEquals(R.string.pc_window_maximize, specs[5].labelResId)
        assertEquals(PcWindowControlAction.MaximizeFocused, specs[5].command.action)
        assertEquals(R.string.pc_window_close, specs[6].labelResId)
        assertEquals(PcWindowControlAction.CloseFocused, specs[6].command.action)
    }
}
