package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.techniques.AccessTechnique
import org.junit.Assert.assertEquals
import org.junit.Test

class TemporaryScanModeSessionTest {
    @Test
    fun startingFromPointScanSwitchesToItemScanAndRestores() {
        val controller = FakeScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.ITEM_SCAN)

        session.start()
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())

        session.close()
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
    }

    @Test
    fun startingFromRadarSwitchesToItemScanAndRestores() {
        val controller = FakeScanModeController(AccessTechnique.Technique.RADAR)
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.ITEM_SCAN)

        session.start()
        session.close()

        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun startingFromItemScanDoesNotRestore() {
        val controller = FakeScanModeController(AccessTechnique.Technique.ITEM_SCAN)
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.ITEM_SCAN)

        session.start()
        session.close()

        assertEquals(0, controller.restoreCalls)
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun closeDoesNotOverrideUserTechniqueChange() {
        val controller = FakeScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.ITEM_SCAN)

        session.start()
        controller.setRadarType()
        session.close()

        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    private class FakeScanModeController(initialTechnique: String) : ScanModeController {
        private var technique = initialTechnique
        var restoreCalls = 0

        override fun currentTechnique(): String = technique

        override fun setPointScanType() {
            restoreCalls++
            technique = AccessTechnique.Technique.POINT_SCAN
        }

        override fun setRadarType() {
            restoreCalls++
            technique = AccessTechnique.Technique.RADAR
        }

        override fun setItemScanType() {
            technique = AccessTechnique.Technique.ITEM_SCAN
        }
    }
}
