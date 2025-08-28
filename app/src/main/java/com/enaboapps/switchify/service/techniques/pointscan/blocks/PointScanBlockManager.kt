package com.enaboapps.switchify.service.techniques.pointscan.blocks

import android.content.Context
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.scanning.tree.ScanTreeCallback
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.utils.ScreenUtils

class PointScanBlockManager(
    private val context: Context,
    private val onBlockSelected: (Int) -> Unit
) : ScanTreeCallback {
    private var blocks: List<PointScanBlock> = emptyList()

    private val cursorBlockGridUI = PointScanBlockGridUI(context)

    private val scanTree = ScanTree(
        context,
        stopScanningOnSelect = true,
        callback = this
    )

    override fun onScanTreeReset() {
        cursorBlockGridUI.reset()
    }

    fun initializeBlocks() {
        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)

        val gridSize = PointScanSettings.getCursorBlockCount()
        val totalBlocks = gridSize * gridSize

        val blockWidth = screenWidth / gridSize
        val blockHeight = screenHeight / gridSize

        blocks = List(totalBlocks) { index ->
            val row = index / gridSize
            val column = index % gridSize

            val left = column * blockWidth
            val top = row * blockHeight
            val right = left + blockWidth
            val bottom = top + blockHeight

            PointScanBlock(index, row, column, left, top, right, bottom)
        }

        val nodes = blocks.map { Node.fromPointScanBlock(it) }.toList()
        nodes.forEachIndexed { index, node -> node.setOnSelect { onBlockSelected(index) } }
        scanTree.setSpeed(PointScanSettings.getCursorBlockScanRate())
        scanTree.buildTree(nodes)
    }

    fun showBlocks() {
        cursorBlockGridUI.showGrid()
    }

    fun cleanup() {
        blocks = emptyList()
        scanTree.cleanup()
        cursorBlockGridUI.reset()
    }

    fun resetForNextUse() {
        scanTree.stopScanningAndReset()
        cursorBlockGridUI.reset()
    }

    fun getScanTree(): ScanTree {
        return scanTree
    }

    fun getBlock(index: Int): PointScanBlock? {
        return blocks.getOrNull(index)
    }
}
