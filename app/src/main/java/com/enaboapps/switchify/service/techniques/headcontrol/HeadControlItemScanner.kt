package com.enaboapps.switchify.service.techniques.headcontrol

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import kotlin.math.pow
import kotlin.math.sqrt

class HeadControlItemScanner {
    private var nodes: List<ScanNodeInterface> = emptyList()
    private var selectedIndex: Int = -1
    private var nodePositions: List<Pair<Float, Float>> = emptyList()

    fun setNodes(nodeList: List<ScanNodeInterface>) {
        nodes = nodeList
        cacheNodePositions()
        selectedIndex = if (nodes.isNotEmpty()) 0 else -1
        highlightCurrent()
    }

    private fun cacheNodePositions() {
        nodePositions = nodes.map { node ->
            val centerX = node.getLeft() + node.getWidth() / 2f
            val centerY = node.getTop() + node.getHeight() / 2f
            Pair(centerX, centerY)
        }
    }

    fun updateSelection(headX: Float, headY: Float) {
        if (nodes.isEmpty()) return

        var closestIndex = 0
        var minDistance = Float.MAX_VALUE

        nodePositions.forEachIndexed { index, (nodeX, nodeY) ->
            val distance = sqrt((headX - nodeX).pow(2) + (headY - nodeY).pow(2))
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = index
            }
        }

        if (closestIndex != selectedIndex) {
            unhighlightCurrent()
            selectedIndex = closestIndex
            highlightCurrent()
        }
    }

    fun performSelection(): Boolean {
        return if (selectedIndex in nodes.indices) {
            nodes[selectedIndex].select()
            true
        } else {
            false
        }
    }

    private fun highlightCurrent() {
        if (selectedIndex in nodes.indices) {
            nodes[selectedIndex].highlight()
        }
    }

    private fun unhighlightCurrent() {
        if (selectedIndex in nodes.indices) {
            nodes[selectedIndex].unhighlight()
        }
    }

    fun clear() {
        unhighlightCurrent()
        nodes = emptyList()
        nodePositions = emptyList()
        selectedIndex = -1
    }
}

