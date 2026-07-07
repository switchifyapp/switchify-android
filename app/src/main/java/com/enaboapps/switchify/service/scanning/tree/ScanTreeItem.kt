package com.enaboapps.switchify.service.scanning.tree

import android.graphics.Rect
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.nodes.NodeSpeaker
import com.enaboapps.switchify.service.techniques.nodes.scanners.NodeScannerUI
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets

/**
 * This class represents an item in the 2D scan tree
 * @property children The children of the item
 * @property y The y coordinate of the item
 * @property isGroupScanEnabled Whether group scanning is enabled (used for splitting logic)
 */
class ScanTreeItem(
    val children: List<ScanNodeInterface>,
    val y: Int,
    private val isGroupScanEnabled: Boolean,
    private val boundsUi: ScanTreeBoundsUi = NodeScannerBoundsUi
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

    fun highlightEscape(groupIndex: Int? = null) {
        if (groupIndex == null) {
            highlightEntireItemEscape()
        } else {
            highlightGroupEscape(groupIndex)
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
        showRowBoundsFor(children, isEscape = false)
    }

    private fun highlightEntireItemEscape() {
        showRowBoundsFor(children, isEscape = true)
    }

    private fun highlightGroup(groupIndex: Int) {
        val group = groups.getOrNull(groupIndex) ?: return
        // Defensive: splitIntoGroups doesn't currently produce a 1-node group,
        // but the invariant ("a 1-item container is the item") belongs here.
        if (group.size == 1) {
            group[0].highlight()
            return
        }
        showRowBoundsFor(group, isEscape = false)
    }

    private fun highlightGroupEscape(groupIndex: Int) {
        val group = groups.getOrNull(groupIndex) ?: return
        showRowBoundsFor(group, isEscape = true)
    }

    private fun showRowBoundsFor(nodes: List<ScanNodeInterface>, isEscape: Boolean) {
        val target = commonOverlayTarget(nodes)
        val bounds = aggregateBounds(nodes, target)
        if (isEscape) {
            boundsUi.showEscapeBounds(
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height(),
                target
            )
        } else {
            boundsUi.showRowBounds(
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height(),
                target
            )
        }
    }

    private fun commonOverlayTarget(nodes: List<ScanNodeInterface>): OverlayTarget {
        val targets = nodes.mapNotNull { (it as? Node)?.getOverlayTargetForHighlight() }
        if (targets.size == nodes.size && targets.isNotEmpty()) {
            val distinctTargets = targets.distinct()
            if (distinctTargets.size == 1) return distinctTargets.single()

            val displayIds = distinctTargets.map { target ->
                when (target) {
                    is OverlayTarget.Display -> target.displayId
                    is OverlayTarget.Window -> target.displayId
                }
            }.distinct()
            if (displayIds.size == 1) {
                val displayId = displayIds.single()
                return OverlayTarget.Display(
                    displayId = displayId,
                    forceSurface = displayId != OverlayTargets.DEFAULT_DISPLAY_ID
                )
            }
        }
        return targets.firstOrNull()?.let { firstTarget ->
            val displayId = displayIdFor(firstTarget)
            OverlayTarget.Display(
                displayId = displayId,
                forceSurface = displayId != OverlayTargets.DEFAULT_DISPLAY_ID
            )
        } ?: OverlayTargets.defaultDisplay()
    }

    private fun aggregateBounds(nodes: List<ScanNodeInterface>, target: OverlayTarget): Rect {
        val targetDisplayId = displayIdFor(target)
        val targetNodes = nodes.filter { node ->
            val nodeTarget = (node as? Node)?.getOverlayTargetForHighlight()
            nodeTarget == null || displayIdFor(nodeTarget) == targetDisplayId
        }.ifEmpty {
            nodes.take(1)
        }
        val bounds = targetNodes.map { node ->
            (node as? Node)?.getOverlayHighlightBounds(target) ?: Rect(
                node.getLeft(),
                node.getTop(),
                node.getLeft() + node.getWidth(),
                node.getTop() + node.getHeight()
            )
        }
        val left = bounds.minOf { it.left }
        val top = bounds.minOf { it.top }
        val right = bounds.maxOf { it.right }
        val bottom = bounds.maxOf { it.bottom }
        return Rect(left, top, right, bottom)
    }

    private fun displayIdFor(target: OverlayTarget): Int {
        return when (target) {
            is OverlayTarget.Display -> target.displayId
            is OverlayTarget.Window -> target.displayId
        }
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
                    unhighlightAggregate(children)
                }
            }

            groupIndex != null && nodeIndex != null -> groups.getOrNull(groupIndex)
                ?.getOrNull(nodeIndex)?.unhighlight()

            groupIndex != null && nodeIndex == null -> {
                val group = groups.getOrNull(groupIndex)
                if (group?.size == 1) {
                    group[0].unhighlight()
                } else {
                    group?.let { unhighlightAggregate(it) } ?: boundsUi.hideAll()
                }
            }

            else -> throw IllegalArgumentException("Invalid unhighlight parameters")
        }
    }

    fun unhighlightEscape(groupIndex: Int? = null) {
        if (groupIndex == null) {
            unhighlightAggregate(children)
            return
        }

        groups.getOrNull(groupIndex)?.let { unhighlightAggregate(it) } ?: boundsUi.hideAll()
    }

    private fun unhighlightAggregate(nodes: List<ScanNodeInterface>) {
        nodes.forEach { it.unhighlight() }
        boundsUi.hideAll()
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

interface ScanTreeBoundsUi {
    fun showRowBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget
    )

    fun showEscapeBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget
    )

    fun hideAll()
}

private object NodeScannerBoundsUi : ScanTreeBoundsUi {
    override fun showRowBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget
    ) {
        NodeScannerUI.instance.showRowBounds(x, y, width, height, target)
    }

    override fun showEscapeBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget
    ) {
        NodeScannerUI.instance.showEscapeBounds(x, y, width, height, target)
    }

    override fun hideAll() {
        NodeScannerUI.instance.hideAll()
    }
}
