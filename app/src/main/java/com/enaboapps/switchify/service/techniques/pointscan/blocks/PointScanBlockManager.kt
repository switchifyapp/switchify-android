package com.enaboapps.switchify.service.techniques.pointscan.blocks

import android.content.Context
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.scanning.tree.ScanTreeCallback
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings
import com.enaboapps.switchify.service.utils.ScreenUtils

class PointScanBlockManager(
    private val context: Context,
    private val onBlockSelected: (Int) -> Unit
) : ScanTreeCallback {
    private var blocks: List<PointScanBlock> = emptyList()
    private var isInitialized = false

    private val cursorBlockGridUI = PointScanBlockGridUI(context)

    private val scanTree = ScanTree(
        context,
        stopScanningOnSelect = true,
        callback = this,
        visualEffectsEnabled = true
    )

    override fun onScanTreeReset() {
        cursorBlockGridUI.hideGrid()
    }

    override fun onScanTreeStarted() {
        cursorBlockGridUI.showGrid()
    }

    override fun onScanTreeStopped() {
        cursorBlockGridUI.hideGrid()
    }

    fun initializeBlocks() {
        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)

        blocks = PointScanGridGeometry.blocks(screenWidth, screenHeight, PointScanSettings.getCursorBlockCount())

        val nodes = blocks.map { Node.fromPointScanBlock(it) }.toList()
        nodes.forEachIndexed { index, node -> node.setOnSelect { onBlockSelected(index) } }
        scanTree.setSpeed(PointScanSettings.getCursorBlockScanRate())
        scanTree.buildTree(nodes)
        isInitialized = true
    }

    fun cleanup() {
        blocks = emptyList()
        scanTree.cleanup()
        cursorBlockGridUI.reset()
    }

    /**
     * Stops block scanning but keeps the grid on screen, dimmed, while the
     * crosshair scans inside the chosen block.
     */
    fun enterLinePhase() {
        cursorBlockGridUI.holdForLinePhase()
        scanTree.stopScanningAndReset()
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

    fun isInitialized(): Boolean {
        return isInitialized
    }
}
