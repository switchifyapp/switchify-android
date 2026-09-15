package com.enaboapps.switchify.service.techniques.nodes.scanners

import com.enaboapps.switchify.service.scanning.tree.ScanTreeItem
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.NodeScanSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertSame
import com.enaboapps.switchify.service.techniques.pointscan.blocks.PointScanBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseNodeScannerTest {
    @Test
    fun equalVisualSnapshotsStillPublishFreshReferences() {
        val previous = listOf(testNode("Mouse"))
        val next = listOf(testNode("Mouse"))

        assertEquals(previous, next)
        val snapshots = MutableStateFlow(NodeScanSnapshot(previous, "window", 1))
        snapshots.value = NodeScanSnapshot(next, "window", 2)
        assertSame(next.first(), snapshots.value.nodes.first())
    }

    @Test
    fun configurationRefreshRebuildsUnchangedNodes() {
        val nodes = listOf("first", "second", "third", "fourth")
        var rebuiltNodes: List<String>? = null

        val refreshed = refreshScannerConfiguration(
            nodes = nodes,
            isAutoScanning = { false },
            rebuild = { rebuiltNodes = it },
            resumeAutoScanning = { error("Manual scan must not resume") }
        )

        assertTrue(refreshed)
        assertEquals(nodes, rebuiltNodes)
    }

    @Test
    fun configurationRefreshAppliesChangedGrouping() {
        val nodes = listOf(
            testNode("first"),
            testNode("second"),
            testNode("third"),
            testNode("fourth"),
            testNode("fifth"),
            testNode("sixth"),
            testNode("seventh"),
            testNode("eighth")
        )
        var groupScanEnabled = false
        var rebuiltItem: ScanTreeItem? = null
        val rebuild = { currentNodes: List<Node> ->
            rebuiltItem = ScanTreeItem(currentNodes, 0, groupScanEnabled)
        }

        refreshScannerConfiguration(nodes, { false }, rebuild, {})
        assertEquals(1, rebuiltItem?.getGroupCount())

        groupScanEnabled = true
        refreshScannerConfiguration(nodes, { false }, rebuild, {})
        assertEquals(3, rebuiltItem?.getGroupCount())

        groupScanEnabled = false
        refreshScannerConfiguration(nodes, { false }, rebuild, {})
        assertEquals(1, rebuiltItem?.getGroupCount())
    }

    @Test
    fun configurationRefreshResumesRunningAutoScan() {
        var resumeCount = 0

        refreshScannerConfiguration(
            nodes = listOf("first"),
            isAutoScanning = { true },
            rebuild = {},
            resumeAutoScanning = { resumeCount++ }
        )

        assertEquals(1, resumeCount)
    }

    @Test
    fun configurationRefreshLeavesManualScanStopped() {
        var resumeCount = 0

        refreshScannerConfiguration(
            nodes = listOf("first"),
            isAutoScanning = { false },
            rebuild = {},
            resumeAutoScanning = { resumeCount++ }
        )

        assertEquals(0, resumeCount)
    }

    @Test
    fun configurationRefreshDoesNothingWithoutNodeSnapshot() {
        var rebuildCount = 0

        val refreshed = refreshScannerConfiguration<String>(
            nodes = null,
            isAutoScanning = { true },
            rebuild = { rebuildCount++ },
            resumeAutoScanning = { error("Inactive scanner must not resume") }
        )

        assertFalse(refreshed)
        assertEquals(0, rebuildCount)
    }

    private fun testNode(contentDescription: String): Node {
        return Node.fromPointScanBlock(
            PointScanBlock(
                position = 0,
                row = 0,
                column = 0,
                left = 10,
                top = 20,
                right = 110,
                bottom = 120
            )
        ).apply {
            setContentDescription(contentDescription)
        }
    }
}
