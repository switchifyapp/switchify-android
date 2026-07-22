package com.enaboapps.switchify.service.techniques.nodes.scanners

import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeScannerHighlightStateTest {
    private val displayTarget = OverlayTarget.Display(displayId = 0)
    private val windowTarget = OverlayTarget.Window(
        displayId = 0,
        accessibilityWindowId = 12,
        windowType = 1
    )

    @Test
    fun firstHighlightAttaches() {
        assertEquals(
            NodeScannerHighlightTransition.ATTACH,
            NodeScannerHighlightTransitions.show(null, spec(NodeScannerHighlightRole.ROW))
        )
    }

    @Test
    fun roleChangesOnSameTargetUpdateSingleHighlight() {
        val roles = listOf(
            NodeScannerHighlightRole.ROW to NodeScannerHighlightRole.ITEM,
            NodeScannerHighlightRole.ITEM to NodeScannerHighlightRole.ROW,
            NodeScannerHighlightRole.ESCAPE to NodeScannerHighlightRole.ITEM,
            NodeScannerHighlightRole.ITEM to NodeScannerHighlightRole.ESCAPE,
            NodeScannerHighlightRole.ROW to NodeScannerHighlightRole.ESCAPE
        )

        roles.forEach { (currentRole, nextRole) ->
            assertEquals(
                NodeScannerHighlightTransition.UPDATE,
                NodeScannerHighlightTransitions.show(
                    state(currentRole),
                    spec(nextRole)
                )
            )
        }
    }

    @Test
    fun repeatedRoleOnSameTargetUpdatesSingleHighlight() {
        assertEquals(
            NodeScannerHighlightTransition.UPDATE,
            NodeScannerHighlightTransitions.show(
                state(NodeScannerHighlightRole.ITEM),
                spec(NodeScannerHighlightRole.ITEM)
            )
        )
    }

    @Test
    fun targetChangeReplacesHighlight() {
        assertEquals(
            NodeScannerHighlightTransition.REPLACE_TARGET,
            NodeScannerHighlightTransitions.show(
                state(NodeScannerHighlightRole.ITEM),
                spec(NodeScannerHighlightRole.ROW, windowTarget)
            )
        )
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

    private fun spec(
        role: NodeScannerHighlightRole,
        target: OverlayTarget = displayTarget
    ): NodeScannerHighlightSpec {
        return NodeScannerHighlightSpec(role, 10, 20, 100, 50, target)
    }
}
