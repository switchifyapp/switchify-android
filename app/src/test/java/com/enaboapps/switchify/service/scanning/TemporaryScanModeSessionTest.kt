package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.techniques.AccessTechnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertFalse(controller.isTemporaryTechniqueActive())
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
    fun startAppliesPointScanTargetAndRestoresItemScan() {
        val controller = FakeScanModeController(AccessTechnique.Technique.ITEM_SCAN)
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.POINT_SCAN)

        session.start()
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())

        session.close()
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun startAppliesRadarTarget() {
        val controller = FakeScanModeController(AccessTechnique.Technique.ITEM_SCAN)
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.RADAR)

        session.start()

        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun closeDoesNotRestoreUnknownPreviousTechnique() {
        val controller = FakeScanModeController("unknown_technique")
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.ITEM_SCAN)

        session.start()
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())

        session.close()
        assertEquals(0, controller.restoreCalls)
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun closeDoesNotOverrideUserTechniqueChange() {
        val controller = FakeScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.ITEM_SCAN)

        session.start()
        controller.setPersistentTechnique(AccessTechnique.Technique.RADAR)
        session.close()

        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun choosingTargetTechniqueManuallyCancelsRestore() {
        val controller = FakeScanModeController(AccessTechnique.Technique.POINT_SCAN)
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.ITEM_SCAN)

        session.start()
        controller.setPersistentTechnique(AccessTechnique.Technique.ITEM_SCAN)
        session.close()

        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
        assertEquals(0, controller.restoreCalls)
    }

    @Test
    fun menuEntryRestoresStoredUnderlyingTechnique() {
        val controller = FakeScanModeController(
            initialTechnique = AccessTechnique.Technique.MENU,
            preferredTechnique = AccessTechnique.Technique.RADAR
        )
        val session = TemporaryScanModeSession(controller, AccessTechnique.Technique.ITEM_SCAN)

        session.start()
        session.close()

        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }
}

internal class FakeScanModeController(
    initialTechnique: String,
    private var preferredTechnique: String = initialTechnique
) : ScanModeController {
    private var technique = initialTechnique
    private var temporary = false
    var restoreCalls = 0

    override fun currentTechnique(): String = technique
    override fun preferredTechnique(): String = preferredTechnique
    override fun isTemporaryTechniqueActive(): Boolean = temporary

    override fun setTemporaryTechnique(technique: String) {
        this.technique = technique
        temporary = true
    }

    override fun restoreTemporaryTechnique(technique: String) {
        if (!temporary) return
        restoreCalls++
        this.technique = technique
        preferredTechnique = technique
        temporary = false
    }

    fun setPersistentTechnique(technique: String) {
        this.technique = technique
        preferredTechnique = technique
        temporary = false
    }
}
