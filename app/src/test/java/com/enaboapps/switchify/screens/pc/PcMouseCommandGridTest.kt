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
        assertEquals(PcControlCommand.DragStart(), commands[3])
    }

    @Test
    fun clickCommandUsesPrimaryTone() {
        val specs = pcClickControlSpecs()

        assertEquals(PcCommandTone.Primary, specs[0].tone)
    }

    @Test
    fun mouseCommandsDoNotUseDestructiveTone() {
        val specs = pcMouseControlSpecs(40)

        assertEquals(false, specs.any { it.tone == PcCommandTone.Destructive })
    }

    @Test
    fun scrollCommandsKeepExistingStep() {
        val commands = pcScrollControlSpecs().map { it.command }

        assertEquals(PcControlCommand.Scroll(0, 5), commands[0])
        assertEquals(PcControlCommand.Scroll(0, -5), commands[1])
    }

    @Test
    fun movementCommandsAreRepeatable() {
        val specs = pcMovementControlSpecs(40)

        assertEquals(true, specs.all { it.repeatable })
    }

    @Test
    fun scrollCommandsAreRepeatable() {
        val specs = pcScrollControlSpecs()

        assertEquals(true, specs.all { it.repeatable })
    }

    @Test
    fun clickCommandsAreNotRepeatable() {
        val specs = pcClickControlSpecs()

        assertEquals(false, specs.any { it.repeatable })
    }

    @Test
    fun allCommandsKeepStableGroupedOrder() {
        val commands = pcMouseControlSpecs(40).map { it.command }

        assertEquals(
            listOf(
                PcControlCommand.Move(-40, -40),
                PcControlCommand.Move(0, -40),
                PcControlCommand.Move(40, -40),
                PcControlCommand.Move(-40, 0),
                PcControlCommand.Move(40, 0),
                PcControlCommand.Move(-40, 40),
                PcControlCommand.Move(0, 40),
                PcControlCommand.Move(40, 40),
                PcControlCommand.LeftClick,
                PcControlCommand.DoubleClick,
                PcControlCommand.RightClick,
                PcControlCommand.DragStart(),
                PcControlCommand.Scroll(0, 5),
                PcControlCommand.Scroll(0, -5)
            ),
            commands
        )
    }

    @Test
    fun compactCommandsPutClickInMovementPadCenter() {
        val commands = pcMouseCompactControlSpecs(40).mapNotNull { it?.command }

        assertEquals(PcControlCommand.LeftClick, commands[5])
    }

    @Test
    fun compactCommandsUseStableScanOrder() {
        val commands = pcMouseCompactControlSpecs(40).mapNotNull { it?.command }

        assertEquals(
            listOf(
                PcControlCommand.Move(-40, -40),
                PcControlCommand.Move(0, -40),
                PcControlCommand.Move(40, -40),
                PcControlCommand.Scroll(0, 5),
                PcControlCommand.Move(-40, 0),
                PcControlCommand.LeftClick,
                PcControlCommand.Move(40, 0),
                PcControlCommand.Move(-40, 40),
                PcControlCommand.Move(0, 40),
                PcControlCommand.Move(40, 40),
                PcControlCommand.Scroll(0, -5),
                PcControlCommand.DoubleClick,
                PcControlCommand.RightClick,
                PcControlCommand.DragStart()
            ),
            commands
        )
    }

    @Test
    fun compactCommandsPadScrollColumnMiddle() {
        val specs = pcMouseCompactControlSpecs(40)

        assertEquals(15, specs.size)
        assertEquals(14, specs.filterNotNull().size)
        assertEquals(null, specs[7])
    }

    @Test
    fun compactCommandsIncludeDragInStableScanOrder() {
        val commands = pcMouseCompactControlSpecs(40).mapNotNull { it?.command }

        assertEquals(PcControlCommand.DragStart(), commands[13])
    }
}
