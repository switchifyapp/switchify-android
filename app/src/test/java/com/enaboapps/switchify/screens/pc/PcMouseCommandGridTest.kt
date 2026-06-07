package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.PcMouseCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class PcMouseCommandGridTest {
    @Test
    fun movementCommandsUseSmallFallbackStep() {
        val commands = pcMouseControlSpecs(40).map { it.command }

        assertEquals(PcMouseCommand.Move(-40, -40), commands[0])
        assertEquals(PcMouseCommand.Move(0, -40), commands[1])
        assertEquals(PcMouseCommand.Move(40, -40), commands[2])
        assertEquals(PcMouseCommand.Move(-40, 0), commands[3])
        assertEquals(PcMouseCommand.Move(40, 0), commands[5])
        assertEquals(PcMouseCommand.Move(-40, 40), commands[6])
        assertEquals(PcMouseCommand.Move(0, 40), commands[7])
        assertEquals(PcMouseCommand.Move(40, 40), commands[8])
    }

    @Test
    fun movementCommandsUseMediumFallbackStep() {
        val commands = pcMouseControlSpecs(80).map { it.command }

        assertEquals(PcMouseCommand.Move(80, 0), commands[5])
    }

    @Test
    fun movementCommandsUseLargeFallbackStep() {
        val commands = pcMouseControlSpecs(160).map { it.command }

        assertEquals(PcMouseCommand.Move(160, 0), commands[5])
    }

    @Test
    fun scrollCommandsKeepExistingStep() {
        val commands = pcMouseControlSpecs(130).map { it.command }

        assertEquals(PcMouseCommand.Scroll(0, 5), commands[11])
        assertEquals(PcMouseCommand.Scroll(0, -5), commands[12])
    }
}
