package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.connection.PcDiagnosticEvent
import com.enaboapps.switchify.pc.transport.PcConnectionStage
import com.enaboapps.switchify.pc.transport.PcConnectionStageOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class PcDiagnosticTextTest {
    @Test
    fun everyDiagnosticHasItsOwnStringResource() {
        assertEquals(PcDiagnosticEvent.entries.size, PcDiagnosticEvent.entries.map { it.messageRes() }.toSet().size)
        assertEquals(PcConnectionStage.entries.size, PcConnectionStage.entries.map { it.descriptionRes() }.toSet().size)
        assertEquals(PcConnectionStageOutcome.entries.size, PcConnectionStageOutcome.entries.map { it.labelRes() }.toSet().size)
    }

    @Test
    fun exportsDiagnosticsAsATimestampedTextFile() {
        assertEquals("switchify-pc-diagnostics-20251009-085320.txt", PcDiagnosticsViewModel.exportFileName(1_760_000_000_123))
        assertEquals("text/plain", PcDiagnosticsViewModel.EXPORT_MIME_TYPE)
    }
}
