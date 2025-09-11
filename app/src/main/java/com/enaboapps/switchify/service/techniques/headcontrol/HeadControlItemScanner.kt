package com.enaboapps.switchify.service.techniques.headcontrol

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import kotlin.math.pow
import kotlin.math.sqrt

class HeadControlItemScanner {
    private var nodes: List<ScanNodeInterface> = emptyList()
    private var selectedIndex: Int = -1
    private var nodePositions: List<Pair<Float, Float>> = emptyList()
    private var lastHeadX: Float? = null
    private var lastHeadY: Float? = null
    private var lastStepTime: Long = 0L
    
    companion object {
        private const val MOVEMENT_THRESHOLD = 10f
        private const val STEP_COOLDOWN_MS = 200L
        private const val DIRECTIONAL_TOLERANCE = 0.75f
        private const val ANGULAR_COS_THRESHOLD = 0.5f
    }

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

        val prevX = lastHeadX
        val prevY = lastHeadY
        lastHeadX = headX
        lastHeadY = headY

        if (prevX == null || prevY == null) return

        val dx = headX - prevX
        val dy = headY - prevY
        val movement = sqrt(dx.pow(2) + dy.pow(2))
        if (movement < MOVEMENT_THRESHOLD) return

        val now = System.currentTimeMillis()
        if (now - lastStepTime < STEP_COOLDOWN_MS) return

        if (selectedIndex !in nodes.indices) return

        val current = nodePositions[selectedIndex]
        val dominantHorizontal = kotlin.math.abs(dx) >= kotlin.math.abs(dy)
        val candidate = if (dominantHorizontal) {
            selectHorizontal(current, dx)
        } else {
            selectVertical(current, dy)
        } ?: selectAngular(current, dx, dy)

        if (candidate != null && candidate != selectedIndex) {
            unhighlightCurrent()
            selectedIndex = candidate
            highlightCurrent()
            lastStepTime = now
        }
    }

    fun initializeSelectionNear(headX: Float, headY: Float) {
        if (nodes.isEmpty()) return
        var closestIndex = 0
        var minDistance = Float.MAX_VALUE
        nodePositions.forEachIndexed { index, (nodeX, nodeY) ->
            val d = sqrt((headX - nodeX).pow(2) + (headY - nodeY).pow(2))
            if (d < minDistance) {
                minDistance = d
                closestIndex = index
            }
        }
        if (closestIndex != selectedIndex) {
            unhighlightCurrent()
            selectedIndex = closestIndex
            highlightCurrent()
        }
        lastHeadX = headX
        lastHeadY = headY
    }

    fun stepLeft() {
        if (selectedIndex !in nodes.indices) return
        val current = nodePositions[selectedIndex]
        val candidate = selectHorizontal(current, -1f)
        if (candidate != null && candidate != selectedIndex) {
            unhighlightCurrent()
            selectedIndex = candidate
            highlightCurrent()
        }
    }

    fun stepRight() {
        if (selectedIndex !in nodes.indices) return
        val current = nodePositions[selectedIndex]
        val candidate = selectHorizontal(current, 1f)
        if (candidate != null && candidate != selectedIndex) {
            unhighlightCurrent()
            selectedIndex = candidate
            highlightCurrent()
        }
    }

    fun stepUp() {
        if (selectedIndex !in nodes.indices) return
        val current = nodePositions[selectedIndex]
        val candidate = selectVertical(current, -1f)
        if (candidate != null && candidate != selectedIndex) {
            unhighlightCurrent()
            selectedIndex = candidate
            highlightCurrent()
        }
    }

    fun stepDown() {
        if (selectedIndex !in nodes.indices) return
        val current = nodePositions[selectedIndex]
        val candidate = selectVertical(current, 1f)
        if (candidate != null && candidate != selectedIndex) {
            unhighlightCurrent()
            selectedIndex = candidate
            highlightCurrent()
        }
    }

    private fun selectHorizontal(current: Pair<Float, Float>, dx: Float): Int? {
        val right = dx > 0
        val (cx, cy) = current
        val tolerance = currentNodeHeight() * DIRECTIONAL_TOLERANCE
        var bestIndex: Int? = null
        var bestDelta = Float.MAX_VALUE
        nodePositions.forEachIndexed { i, (nx, ny) ->
            if (i == selectedIndex) return@forEachIndexed
            if (right && nx <= cx) return@forEachIndexed
            if (!right && nx >= cx) return@forEachIndexed
            if (kotlin.math.abs(ny - cy) > tolerance) return@forEachIndexed
            val delta = kotlin.math.abs(nx - cx)
            if (delta < bestDelta) {
                bestDelta = delta
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun selectVertical(current: Pair<Float, Float>, dy: Float): Int? {
        val down = dy > 0
        val (cx, cy) = current
        val tolerance = currentNodeWidth() * DIRECTIONAL_TOLERANCE
        var bestIndex: Int? = null
        var bestDelta = Float.MAX_VALUE
        nodePositions.forEachIndexed { i, (nx, ny) ->
            if (i == selectedIndex) return@forEachIndexed
            if (down && ny <= cy) return@forEachIndexed
            if (!down && ny >= cy) return@forEachIndexed
            if (kotlin.math.abs(nx - cx) > tolerance) return@forEachIndexed
            val delta = kotlin.math.abs(ny - cy)
            if (delta < bestDelta) {
                bestDelta = delta
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun selectAngular(current: Pair<Float, Float>, dx: Float, dy: Float): Int? {
        val (cx, cy) = current
        val mag = sqrt(dx.pow(2) + dy.pow(2))
        if (mag == 0f) return null
        val vx = dx / mag
        val vy = dy / mag
        var bestIndex: Int? = null
        var bestProj = Float.MAX_VALUE
        nodePositions.forEachIndexed { i, (nx, ny) ->
            if (i == selectedIndex) return@forEachIndexed
            val wx = nx - cx
            val wy = ny - cy
            val proj = vx * wx + vy * wy
            if (proj <= 0) return@forEachIndexed
            val wxMag = sqrt(wx.pow(2) + wy.pow(2))
            if (wxMag == 0f) return@forEachIndexed
            val cosTheta = proj / wxMag
            if (cosTheta < ANGULAR_COS_THRESHOLD) return@forEachIndexed
            if (proj < bestProj) {
                bestProj = proj
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun currentNodeWidth(): Float {
        return if (selectedIndex in nodes.indices) nodes[selectedIndex].getWidth().toFloat() else 200f
    }

    private fun currentNodeHeight(): Float {
        return if (selectedIndex in nodes.indices) nodes[selectedIndex].getHeight().toFloat() else 200f
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
