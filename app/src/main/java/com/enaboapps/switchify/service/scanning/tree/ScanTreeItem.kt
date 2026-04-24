package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.techniques.nodes.NodeSpeaker
import com.enaboapps.switchify.service.techniques.nodes.scanners.NodeScannerUI

/**
 * This class represents an item in the 2D scan tree
 * @property children The children of the item
 * @property y The y coordinate of the item
 * @property isGroupScanEnabled Whether group scanning is enabled (used for splitting logic)
 */
class ScanTreeItem(
    val children: List<ScanNodeInterface>,
    val y: Int,
    private val isGroupScanEnabled: Boolean
) {
    private val groups: List<List<ScanNodeInterface>> = splitIntoGroups(children)

    /**
     * This function highlights the item, group, or specific node
     * @param groupIndex The index of the group to highlight, or null to highlight the entire item
     * @param nodeIndex The index of the node within the group to highlight, or null to highlight the entire group
     */
    fun highlight(groupIndex: Int? = null, nodeIndex: Int? = null) {
        when {
            groupIndex == null && nodeIndex == null -> highlightEntireItem()
            groupIndex != null && nodeIndex == null -> highlightGroup(groupIndex)
            groupIndex != null && nodeIndex != null -> highlightNode(groupIndex, nodeIndex)
            else -> throw IllegalArgumentException("Invalid highlight parameters")
        }
    }

    private fun highlightEntireItem() {
        // A single-child row is semantically the node itself — highlight with the
        // node colour (secondary) instead of the row colour (primary) so the
        // visual matches what the selector and speaker already do for this case.
        if (isSingleNode()) {
            children[0].highlight()
            return
        }
        NodeScannerUI.instance.showRowBounds(getX(), y, getWidth(), getHeight())
    }

    private fun highlightGroup(groupIndex: Int) {
        val group = groups.getOrNull(groupIndex) ?: return
        // Defensive: splitIntoGroups doesn't currently produce a 1-node group,
        // but the invariant ("a 1-item container is the item") belongs here.
        if (group.size == 1) {
            group[0].highlight()
            return
        }
        val groupX = group.minOf { it.getLeft() }
        val groupWidth = group.maxOf { it.getLeft() + it.getWidth() } - groupX
        val groupY = group.minOf { it.getTop() }
        val groupHeight = group.maxOf { it.getTop() + it.getHeight() } - groupY
        NodeScannerUI.instance.showRowBounds(groupX, groupY, groupWidth, groupHeight)
    }

    private fun highlightNode(groupIndex: Int, nodeIndex: Int) {
        groups.getOrNull(groupIndex)?.getOrNull(nodeIndex)?.highlight()
    }

    /**
     * This function unhighlights the item or specific node.
     * Single-node rows (and 1-node groups) unhighlight via the node's own
     * [ScanNodeInterface.unhighlight] so the matching post-highlight callbacks
     * fire — mirror of the single-node shortcut in [highlightEntireItem] /
     * [highlightGroup]. Without this, consumers that react to node highlight
     * state (e.g. the radial menu's centre-label overlay) never see the
     * un-highlight and their state stays stale.
     * @param groupIndex The index of the group to unhighlight, or null to unhighlight the entire item
     * @param nodeIndex The index of the node within the group to unhighlight, or null to unhighlight the entire group
     */
    fun unhighlight(groupIndex: Int? = null, nodeIndex: Int? = null) {
        when {
            groupIndex == null && nodeIndex == null -> {
                if (isSingleNode()) {
                    children[0].unhighlight()
                } else {
                    NodeScannerUI.instance.hideAll()
                }
            }

            groupIndex != null && nodeIndex != null -> groups.getOrNull(groupIndex)
                ?.getOrNull(nodeIndex)?.unhighlight()

            groupIndex != null && nodeIndex == null -> {
                val group = groups.getOrNull(groupIndex)
                if (group?.size == 1) {
                    group[0].unhighlight()
                } else {
                    NodeScannerUI.instance.hideAll()
                }
            }

            else -> throw IllegalArgumentException("Invalid unhighlight parameters")
        }
    }

    /**
     * This function speaks the row or group of nodes.
     * A 1-item row collapses to the node's own description rather than the
     * "Row of 1 items starting at X" phrasing NodeSpeaker.speakNodes would
     * produce — matching the single-node shortcut on the selector/highlighter.
     * @param isGroup Whether the nodes are part of a group
     */
    fun speakNodes(isGroup: Boolean) {
        if (children.size == 1) {
            NodeSpeaker.speakNode(children[0])
            return
        }
        NodeSpeaker.speakNodes(children, isGroup)
    }

    /**
     * This function speaks the a specific group of nodes.
     * Defensive: if the group has one node, speak that node directly instead
     * of announcing "Group of 1 items starting at X".
     * @param groupIndex The index of the group to speak
     */
    fun speakGroup(groupIndex: Int) {
        val group = groups.getOrNull(groupIndex) ?: return
        if (group.size == 1) {
            NodeSpeaker.speakNode(group[0])
            return
        }
        NodeSpeaker.speakNodes(group, true)
    }

    /**
     * This function speaks an individual node
     * @param groupIndex The index of the group to speak (null if not in group scanning mode)
     * @param nodeIndex The index of the node to speak
     */
    fun speakNode(groupIndex: Int?, nodeIndex: Int) {
        val node = children.getOrNull(nodeIndex) ?: return
        if (groupIndex != null) {
            val nodeInGroup = groups.getOrNull(groupIndex)?.getOrNull(nodeIndex) ?: return
            NodeSpeaker.speakNode(nodeInGroup)
        } else {
            NodeSpeaker.speakNode(node)
        } ?: println("Node not found")
    }

    fun getX(): Int = children.minOf { it.getLeft() }
    fun getWidth(): Int = children.maxOf { it.getLeft() + it.getWidth() } - getX()
    fun getHeight(): Int = children.maxOf { it.getTop() + it.getHeight() } - y

    fun getGroupCount(): Int = groups.size
    fun getNodeCount(groupIndex: Int): Int = groups.getOrNull(groupIndex)?.size ?: 0

    fun selectNode(groupIndex: Int, nodeIndex: Int) {
        groups.getOrNull(groupIndex)?.getOrNull(nodeIndex)?.select()
    }

    fun isSingleNode(): Boolean = children.size == 1

    fun selectSingleNodeIfApplicable(): Boolean {
        if (isSingleNode()) {
            children[0].select()
            return true
        }
        return false
    }

    fun isGrouped(): Boolean = groups.size > 1

    /**
     * This function splits the children into groups
     * If group scanning is enabled and there are 4 or more nodes, it splits the row in half
     * Otherwise, it creates a single group with all nodes
     * @param nodes The list of nodes to split into groups
     * @return A list of groups, where each group is a list of nodes
     */
    private fun splitIntoGroups(nodes: List<ScanNodeInterface>): List<List<ScanNodeInterface>> {
        val sortedNodes = nodes.sortedBy { it.getLeft() }
        return if (isGroupScanEnabled && sortedNodes.size >= 4) {
            val midpoint = sortedNodes.size / 2
            listOf(
                sortedNodes.subList(0, midpoint),
                sortedNodes.subList(midpoint, sortedNodes.size)
            )
        } else {
            listOf(sortedNodes)
        }
    }
}