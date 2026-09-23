package com.enaboapps.switchify.service.techniques.nodes.scanners

import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeScannerHighlightStateTest {
    private val displayTarget = OverlayTarget.Display(displayId = 0)

    @Test
    fun scopedHideRequiresMatchingOwnerAndRole() {
        for (role in NodeScannerHighlightRole.entries) {
            val current = NodeScannerHighlightState(role, displayTarget, "menu")
            assertEquals(NodeScannerHighlightTransition.IGNORE,
                NodeScannerHighlightTransitions.hide(current, setOf(role), "system"))
            assertEquals(NodeScannerHighlightTransition.REMOVE,
                NodeScannerHighlightTransitions.hide(current, setOf(role), "menu"))
            assertEquals(NodeScannerHighlightTransition.REMOVE,
                NodeScannerHighlightTransitions.hide(current, setOf(role)))
            assertEquals(NodeScannerHighlightTransition.IGNORE,
                NodeScannerHighlightTransitions.hide(current.copy(owner = null), setOf(role), "menu"))
        }
    }

    @Test
    fun itemHideOnlyRemovesItem() {
        val itemRoles = setOf(NodeScannerHighlightRole.ITEM)

        assertEquals(
            NodeScannerHighlightTransition.REMOVE,
            NodeScannerHighlightTransitions.hide(
                state(NodeScannerHighlightRole.ITEM),
                itemRoles
            )
        )
        assertEquals(
            NodeScannerHighlightTransition.IGNORE,
            NodeScannerHighlightTransitions.hide(
                state(NodeScannerHighlightRole.ROW),
                itemRoles
            )
        )
    }

    @Test
    fun rowHideRemovesRowAndEscapeButNotItem() {
        val rowRoles = setOf(
            NodeScannerHighlightRole.ROW,
            NodeScannerHighlightRole.ESCAPE
        )

        assertEquals(
            NodeScannerHighlightTransition.REMOVE,
            NodeScannerHighlightTransitions.hide(
                state(NodeScannerHighlightRole.ROW),
                rowRoles
            )
        )
        assertEquals(
            NodeScannerHighlightTransition.REMOVE,
            NodeScannerHighlightTransitions.hide(
                state(NodeScannerHighlightRole.ESCAPE),
                rowRoles
            )
        )
        assertEquals(
            NodeScannerHighlightTransition.IGNORE,
            NodeScannerHighlightTransitions.hide(
                state(NodeScannerHighlightRole.ITEM),
                rowRoles
            )
        )
    }

    @Test
    fun hideWithoutActiveHighlightIsIgnored() {
        assertEquals(
            NodeScannerHighlightTransition.IGNORE,
            NodeScannerHighlightTransitions.hide(
                null,
                NodeScannerHighlightRole.entries.toSet()
            )
        )
    }

    @Test
    fun staleEpochIsRejected() {
        assertTrue(NodeScannerHighlightTransitions.isCurrentEpoch(4L, 4L))
        assertFalse(NodeScannerHighlightTransitions.isCurrentEpoch(3L, 4L))
    }

    private fun state(
        role: NodeScannerHighlightRole,
        target: OverlayTarget = displayTarget
    ): NodeScannerHighlightState {
        return NodeScannerHighlightState(role, target)
    }
}
