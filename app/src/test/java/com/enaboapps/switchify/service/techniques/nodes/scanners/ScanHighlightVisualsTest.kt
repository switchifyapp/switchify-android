package com.enaboapps.switchify.service.techniques.nodes.scanners

import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import org.junit.Assert.*
import org.junit.Test

class ScanHighlightVisualsTest {
    @Test fun interruptedMoveStartsAtRenderedBounds() {
        val start = ScanHighlightBounds(0f, 10f, 40f, 30f)
        val firstTarget = ScanHighlightBounds(100f, 110f, 80f, 50f)
        val rendered = start.interpolate(firstTarget, 0.5f)
        val next = ScanHighlightBounds(200f, 10f, 100f, 30f)
        assertEquals(rendered, rendered.interpolate(next, 0f))
        assertEquals(ScanHighlightBounds(125f, 35f, 80f, 35f), rendered.interpolate(next, 0.5f))
        assertEquals(next, rendered.interpolate(next, 1f))
    }

    @Test fun invalidBoundsCannotProduceCutouts() {
        assertFalse(ScanHighlightBounds(0f, 0f, 0f, 10f).isUsable)
        assertFalse(ScanHighlightBounds(Float.NaN, 0f, 10f, 10f).isUsable)
        assertTrue(ScanHighlightBounds(-5f, 0f, 10f, 10f).isUsable)
    }

    @Test fun unhighlightAndShowKeepOnlyFinalTarget() {
        val batch = NodeScannerVisualBatch("menu", 4)
        batch.hide(setOf(NodeScannerHighlightRole.ROW))
        batch.show(spec(NodeScannerHighlightRole.ITEM, 100))
        batch.hide(setOf(NodeScannerHighlightRole.ITEM))
        batch.show(spec(NodeScannerHighlightRole.ITEM, 200))
        assertEquals(200, batch.spec?.x)
    }

    @Test fun hideWithoutReplacementClearsPendingHighlight() {
        val batch = NodeScannerVisualBatch("menu", 4)
        batch.show(spec(NodeScannerHighlightRole.ROW))
        batch.hide(setOf(NodeScannerHighlightRole.ITEM))
        assertNotNull(batch.spec)
        batch.hide(setOf(NodeScannerHighlightRole.ROW))
        assertNull(batch.spec)
    }

    @Test fun windowAndDisplayChangesCannotAnimateAcrossCoordinates() {
        val window = OverlayTarget.Window(0, 12, 1)
        assertTrue(NodeScannerHighlightTransitions.sameCoordinateSpace(window, window.copy()))
        assertFalse(NodeScannerHighlightTransitions.sameCoordinateSpace(window, window.copy(accessibilityWindowId = 13)))
        assertFalse(NodeScannerHighlightTransitions.sameCoordinateSpace(window, window.copy(displayId = 1)))
        assertFalse(NodeScannerHighlightTransitions.sameCoordinateSpace(window, OverlayTarget.Display(0)))
    }

    @Test fun disabledAnimationsAndLegacyCallersSnap() {
        val previous = spec(NodeScannerHighlightRole.ITEM)
        val next = spec(NodeScannerHighlightRole.ITEM, 50)
        assertTrue(next.animatesFrom(previous, true, true))
        assertFalse(next.animatesFrom(previous, false, true))
        assertFalse(next.animatesFrom(previous, true, false))
        assertFalse(next.copy(owner = "keyboard").animatesFrom(previous, true, true))
        assertFalse(next.copy(owner = null).animatesFrom(previous.copy(owner = null), true, true))
    }

    @Test fun spotlightRequiresScreenBoundsAndFallsBackForFailedTargetsAndPointScan() {
        val window = OverlayTarget.Window(1, 12, 1)
        val item = spec(NodeScannerHighlightRole.ITEM).copy(target = window,
            screenBounds = ScanHighlightBounds(10f, 80f, 40f, 20f))
        assertTrue(item.usesSpotlight(true))
        assertFalse(item.usesSpotlight(false))
        assertFalse(item.copy(screenBounds = null).usesSpotlight(true))
        assertFalse(item.copy(owner = null).usesSpotlight(true))
    }

    @Test fun spotlightTargetsTheWholeDisplayAndOnlySurfacesOffTheDefaultDisplay() {
        val window = OverlayTarget.Window(1, 12, 1)
        val item = spec(NodeScannerHighlightRole.ITEM).copy(target = window)
        assertEquals(OverlayTarget.Display(1, forceSurface = true), item.spotlightTarget())
        assertEquals(OverlayTarget.Display(0), item.copy(target = window.copy(displayId = 0)).spotlightTarget())
        assertEquals(OverlayTarget.Display(0), item.copy(target = OverlayTarget.Display(0)).spotlightTarget())
        assertEquals(OverlayTarget.Display(2, forceSurface = true),
            item.copy(target = OverlayTarget.Display(2)).spotlightTarget())
    }

    @Test fun batchedTargetCarriesCountdownBoundaryDuringRapidTicks() {
        val batch = NodeScannerVisualBatch("menu", 4, 20)
        repeat(100) {
            batch.hide(setOf(NodeScannerHighlightRole.ITEM))
            batch.show(spec(NodeScannerHighlightRole.ITEM, it))
        }
        assertEquals(99, batch.spec?.x)
        assertEquals(20L, batch.spec?.intervalAfterSequence)
    }

    private fun spec(role: NodeScannerHighlightRole, x: Int = 0) =
        NodeScannerHighlightSpec(role, x, 0, 20, 20, OverlayTarget.Display(0), owner = "menu")
}
