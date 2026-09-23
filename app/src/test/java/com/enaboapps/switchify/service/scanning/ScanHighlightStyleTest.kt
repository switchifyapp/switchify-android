package com.enaboapps.switchify.service.scanning

import org.junit.Assert.*
import org.junit.Test

class ScanHighlightStyleTest {
    @Test fun spotlightPreservesSavedBorderAndFillChoicesForLegacyCallers() {
        val border = ScanHighlightStyle.Type("border")
        val fill = ScanHighlightStyle.Type("fill")
        val spotlight = ScanHighlightStyle.Type("spotlight")
        assertEquals(listOf(border, fill, spotlight), ScanHighlightStyle.ALL)
        assertEquals(border, ScanHighlightStyle.legacyTypeToSave(border, spotlight))
        assertEquals(fill, ScanHighlightStyle.legacyTypeToSave(fill, spotlight))
        assertNull(ScanHighlightStyle.legacyTypeToSave(spotlight, spotlight))
        assertEquals(fill, ScanHighlightStyle.legacyTypeToSave(spotlight, fill))
        assertEquals(border, ScanHighlightStyle.legacyTypeToSave(fill, border))
    }

}
