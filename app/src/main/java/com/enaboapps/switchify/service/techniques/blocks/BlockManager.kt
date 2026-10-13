package com.enaboapps.switchify.service.techniques.blocks

import android.content.Context
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.scanning.tree.ScanTreeCallback
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * Scans a grid of [ScanBlock]s so a technique can narrow the screen down to one
 * block before refining the point inside it. Point scan and radar each own one of
 * these with their own grid size and scan rate preferences.
 */
class BlockManager(
    private val context: Context,
    private val gridSizeProvider: () -> Int,
    private val scanRateProvider: () -> Long,
    private val onBlockSelected: (Int) -> Unit
) : ScanTreeCallback {
    private var blocks: List<ScanBlock> = emptyList()
    private var isInitialized = false

    private val gridUI = BlockGridUI(context, gridSizeProvider)

    private val scanTree = ScanTree(
        context,
        stopScanningOnSelect = true,
        callback = this,
        visualEffectsEnabled = true
    )

    override fun onScanTreeReset() {
        gridUI.hideGrid()
    }

    override fun onScanTreeStarted() {
        gridUI.showGrid()
    }

    override fun onScanTreeStopped() {
        gridUI.hideGrid()
    }

    fun initializeBlocks() {
        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)

        blocks = BlockGridGeometry.blocks(screenWidth, screenHeight, gridSizeProvider())

        val nodes = blocks.map { Node.fromScanBlock(it) }.toList()
        nodes.forEachIndexed { index, node -> node.setOnSelect { onBlockSelected(index) } }
        scanTree.setSpeed(scanRateProvider())
        scanTree.buildTree(nodes)
        isInitialized = true
    }

    fun refreshTiming() {
        scanTree.setSpeed(scanRateProvider())
    }

    fun cleanup() {
        blocks = emptyList()
        scanTree.cleanup()
        gridUI.reset()
    }

    /**
     * Stops block scanning but keeps the grid on screen, dimmed, while the
     * technique refines the point inside the chosen block.
     */
    fun enterRefinePhase() {
        gridUI.holdForRefinePhase()
        scanTree.stopScanningAndReset()
    }

    fun resetForNextUse() {
        scanTree.stopScanningAndReset()
        gridUI.reset()
    }

    fun getScanTree(): ScanTree {
        return scanTree
    }

    fun getBlock(index: Int): ScanBlock? {
        return blocks.getOrNull(index)
    }

    fun isInitialized(): Boolean {
        return isInitialized
    }
}
