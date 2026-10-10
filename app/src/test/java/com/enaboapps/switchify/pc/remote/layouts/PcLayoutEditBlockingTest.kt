package com.enaboapps.switchify.pc.remote.layouts

import com.enaboapps.switchify.pc.remote.PcRemoteSessionState
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcLayoutEditBlockingTest {
    private val idle = PcRemoteSessionState()

    @Test
    fun allowsEditingWhenNothingIsHeld() {
        PcRemoteSurface.entries.forEach { assertNull(PcLayoutEditBlocking.block(idle, it, false)) }
        assertNull(PcLayoutEditBlocking.block(idle.copy(streamOpen = true), PcRemoteSurface.Typing, false))
    }

    @Test
    fun blocksWhileRepeatingDraggingOrHoldingModifiers() {
        listOf(
            idle.copy(repeat = "mouse.move"),
            idle.copy(repeat = "keyboard.key"),
            idle.copy(dragging = true),
            idle.copy(modifiers = listOf("Shift"))
        ).forEach { state ->
            assertEquals(state.toString(), PcLayoutEditBlock.HeldInput, PcLayoutEditBlocking.block(state, PcRemoteSurface.Mouse, false))
        }
    }

    @Test
    fun sendingEnterBlocksOnlyTypingAndTakesPriority() {
        assertEquals(PcLayoutEditBlock.SendingEnter, PcLayoutEditBlocking.block(idle.copy(dragging = true), PcRemoteSurface.Typing, true))
        assertNull(PcLayoutEditBlocking.block(idle, PcRemoteSurface.Window, true))
    }
}
