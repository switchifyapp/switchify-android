package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PcMouseCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class PcMouseCommandGridTest {
    @Test
    fun movementCommandsUseSmallFallbackStep() {
        val commands = pcMovementControlSpecs(40).map { it.command }

        assertEquals(PcMouseCommand.Move(-40, -40), commands[0])
        assertEquals(PcMouseCommand.Move(0, -40), commands[1])
        assertEquals(PcMouseCommand.Move(40, -40), commands[2])
        assertEquals(PcMouseCommand.Move(-40, 0), commands[3])
        assertEquals(PcMouseCommand.Move(40, 0), commands[4])
        assertEquals(PcMouseCommand.Move(-40, 40), commands[5])
        assertEquals(PcMouseCommand.Move(0, 40), commands[6])
        assertEquals(PcMouseCommand.Move(40, 40), commands[7])
    }

    @Test
    fun movementCommandsUseMediumFallbackStep() {
        val commands = pcMovementControlSpecs(80).map { it.command }

        assertEquals(PcMouseCommand.Move(80, 0), commands[4])
    }

    @Test
    fun movementCommandsUseLargeFallbackStep() {
        val commands = pcMovementControlSpecs(160).map { it.command }

        assertEquals(PcMouseCommand.Move(160, 0), commands[4])
    }

    @Test
    fun clickCommandsUseStableOrder() {
        val commands = pcClickControlSpecs().map { it.command }

        assertEquals(PcMouseCommand.LeftClick, commands[0])
        assertEquals(PcMouseCommand.DoubleClick, commands[1])
        assertEquals(PcMouseCommand.RightClick, commands[2])
    }

    @Test
    fun scrollCommandsKeepExistingStep() {
        val commands = pcScrollControlSpecs().map { it.command }

        assertEquals(PcMouseCommand.Scroll(0, 5), commands[0])
        assertEquals(PcMouseCommand.Scroll(0, -5), commands[1])
    }

    @Test
    fun allCommandsKeepStableGroupedOrder() {
        val commands = pcMouseControlSpecs(40).map { it.command }

        assertEquals(13, commands.size)
        assertEquals(PcMouseCommand.Move(-40, -40), commands.first())
        assertEquals(PcMouseCommand.LeftClick, commands[8])
        assertEquals(PcMouseCommand.Scroll(0, -5), commands.last())
    }
}
