package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PcControlCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class PcControlCommandGridTest {
    @Test
    fun movementCommandsUseSmallFallbackStep() {
        val commands = pcMovementControlSpecs(40).map { it.command }

        assertEquals(PcControlCommand.Move(-40, -40), commands[0])
        assertEquals(PcControlCommand.Move(0, -40), commands[1])
        assertEquals(PcControlCommand.Move(40, -40), commands[2])
        assertEquals(PcControlCommand.Move(-40, 0), commands[3])
        assertEquals(PcControlCommand.Move(40, 0), commands[4])
        assertEquals(PcControlCommand.Move(-40, 40), commands[5])
        assertEquals(PcControlCommand.Move(0, 40), commands[6])
        assertEquals(PcControlCommand.Move(40, 40), commands[7])
    }

    @Test
    fun movementCommandsUseMediumFallbackStep() {
        val commands = pcMovementControlSpecs(80).map { it.command }

        assertEquals(PcControlCommand.Move(80, 0), commands[4])
    }

    @Test
    fun movementCommandsUseLargeFallbackStep() {
        val commands = pcMovementControlSpecs(160).map { it.command }

        assertEquals(PcControlCommand.Move(160, 0), commands[4])
    }

    @Test
    fun clickCommandsUseStableOrder() {
        val commands = pcClickControlSpecs().map { it.command }

        assertEquals(PcControlCommand.LeftClick, commands[0])
        assertEquals(PcControlCommand.DoubleClick, commands[1])
        assertEquals(PcControlCommand.RightClick, commands[2])
    }

    @Test
    fun scrollCommandsKeepExistingStep() {
        val commands = pcScrollControlSpecs().map { it.command }

        assertEquals(PcControlCommand.Scroll(0, 5), commands[0])
        assertEquals(PcControlCommand.Scroll(0, -5), commands[1])
    }

    @Test
    fun allCommandsKeepStableGroupedOrder() {
        val commands = pcMouseControlSpecs(40).map { it.command }

        assertEquals(13, commands.size)
        assertEquals(PcControlCommand.Move(-40, -40), commands.first())
        assertEquals(PcControlCommand.LeftClick, commands[8])
        assertEquals(PcControlCommand.Scroll(0, -5), commands.last())
    }

    @Test
    fun gridSpecsPlaceClickInCenter() {
        val commands = pcMouseGridSpecs(40).map { it.command }

        assertEquals(9, commands.size)
        assertEquals(PcControlCommand.LeftClick, commands[4])
        assertEquals(
            listOf(
                PcControlCommand.Move(-40, -40),
                PcControlCommand.Move(0, -40),
                PcControlCommand.Move(40, -40)
            ),
            commands.take(3)
        )
        assertEquals(PcControlCommand.Move(-40, 0), commands[3])
        assertEquals(PcControlCommand.Move(40, 0), commands[5])
        assertEquals(
            listOf(
                PcControlCommand.Move(-40, 40),
                PcControlCommand.Move(0, 40),
                PcControlCommand.Move(40, 40)
            ),
            commands.drop(6)
        )
    }

    @Test
    fun gridSpecsRespectMovementStep() {
        val commands = pcMouseGridSpecs(160).map { it.command }

        assertEquals(PcControlCommand.Move(160, 0), commands[5])
    }

    @Test
    fun secondaryClickSpecsExcludeLeftClick() {
        val commands = pcSecondaryClickControlSpecs().map { it.command }

        assertEquals(
            listOf(PcControlCommand.DoubleClick, PcControlCommand.RightClick),
            commands
        )
    }
}
