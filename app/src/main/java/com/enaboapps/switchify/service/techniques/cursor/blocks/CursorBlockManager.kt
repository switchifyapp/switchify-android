package com.enaboapps.switchify.service.techniques.cursor.blocks

import android.content.Context
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.utils.ScreenUtils

class CursorBlockManager(
    private val context: Context
) {
    private val scanSettings = ScanSettings(context)

    private var blocks: List<CursorBlock> = emptyList()

    private val scanTree = ScanTree(
        context,
        stopScanningOnSelect = true
    )

    fun initializeBlocks() {
        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)

        val gridSize = scanSettings.getCursorBlockCount()
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

            CursorBlock(index, row, column, left, top, right, bottom)
        }

        val nodes = blocks.map { Node.fromCursorBlock(it) }.toList()
        scanTree.buildTree(nodes)
    }

    fun reset() {
        blocks = emptyList()
        scanTree.shutdown()
    }

    fun getScanTree(): ScanTree {
        return scanTree
    }

    fun getBlock(index: Int): CursorBlock? {
        return blocks.getOrNull(index)
    }
}