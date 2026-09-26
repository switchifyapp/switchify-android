package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.techniques.AccessTechnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyNodesFallbackTest {
    @Test
    fun entersPointScanTemporarilyAndRestoresItemScanWhenNodesReturn() {
        val controller = FakeScanModeController(AccessTechnique.Technique.ITEM_SCAN)
        val fallback = EmptyNodesFallback(controller)

        assertTrue(fallback.enter())
        assertTrue(fallback.isActive)
        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
        assertTrue(controller.isTemporaryTechniqueActive())

        fallback.onNodesAvailable()
        assertFalse(fallback.isActive)
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
        assertFalse(controller.isTemporaryTechniqueActive())
        assertEquals(1, controller.restoreCalls)
    }

    @Test
    fun doesNotEnterWhenNotItemScanning() {
        val controller = FakeScanModeController(AccessTechnique.Technique.RADAR)
        val fallback = EmptyNodesFallback(controller)

        assertFalse(fallback.enter())
        assertEquals(AccessTechnique.Technique.RADAR, controller.currentTechnique())
    }

    @Test
    fun doesNotStackOnAnExistingTemporaryOverride() {
        val controller = FakeScanModeController(AccessTechnique.Technique.POINT_SCAN)
        controller.setTemporaryTechnique(AccessTechnique.Technique.ITEM_SCAN)
        val fallback = EmptyNodesFallback(controller)

        assertFalse(fallback.enter())
        assertEquals(AccessTechnique.Technique.ITEM_SCAN, controller.currentTechnique())
    }

    @Test
    fun enteringTwiceIsIdempotent() {
        val controller = FakeScanModeController(AccessTechnique.Technique.ITEM_SCAN)
        val fallback = EmptyNodesFallback(controller)

        assertTrue(fallback.enter())
        assertTrue(fallback.enter())
        fallback.onNodesAvailable()

        assertEquals(1, controller.restoreCalls)
    }

    @Test
    fun droppedFallbackDoesNotRestoreAfterExplicitChoice() {
        val controller = FakeScanModeController(AccessTechnique.Technique.ITEM_SCAN)
        val fallback = EmptyNodesFallback(controller)
        fallback.enter()

        controller.setPersistentTechnique(AccessTechnique.Technique.POINT_SCAN)
        fallback.drop()
        fallback.onNodesAvailable()

        assertEquals(AccessTechnique.Technique.POINT_SCAN, controller.currentTechnique())
        assertEquals(0, controller.restoreCalls)
    }
}
