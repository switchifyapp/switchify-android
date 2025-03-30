package com.enaboapps.switchify.service.techniques.cursor

import android.content.Context
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.gestures.GesturePointListener
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.techniques.cursor.blocks.CursorBlock
import com.enaboapps.switchify.service.techniques.cursor.blocks.CursorBlockManager
import com.enaboapps.switchify.service.techniques.cursor.line.CursorLineManager

/**
 * CursorManager class manages the cursor movement, quadrants, and scanning for the Switchify accessibility service.
 *
 * @param context The application context.
 */
class CursorManager(private val context: Context) : AccessTechniqueInterface, GesturePointListener {
    private val blockManager = CursorBlockManager(context, onBlockSelected = { setBlock(it) })
    private val lineManager = CursorLineManager(context, onPointSelected = {
        handleFinalSelectionPoint(it.x.toInt(), it.y.toInt())
    })

    private var previousBlock: CursorBlock? = null

    init {
        GesturePoint.listener = this
        CursorMode.init(context)
        blockManager.initializeBlocks()
    }

    /**
     * Handles gesture point reselection.
     */
    override fun onGesturePointReselect() {
        previousBlock?.let {
            setBlock(it.position)
        }
    }


    /**
     * Sets the block based on the position.
     *
     * @param position The position of the block.
     */
    private fun setBlock(position: Int) {
        val block = blockManager.getBlock(position)
        previousBlock = block
        blockManager.resetForNextUse()
        lineManager.setBlock(block)
        lineManager.startAutoScanning()
    }

    /**
     * Handles the final selection point.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     */
    private fun handleFinalSelectionPoint(x: Int, y: Int) {
        GesturePoint.x = x; GesturePoint.y = y
        SelectionHandler.setSelectAction {
            GestureManager.getInstance().performTap()
        }
        SelectionHandler.performSelectionAction()
        blockManager.resetForNextUse()
        lineManager.resetForNextUse()
    }

    /**
     * Swaps the scanning direction.
     */
    override fun swapScanDirection() {
        getManager().swapScanDirection()
    }

    /**
     * Starts the scanning process.
     */
    override fun startAutoScanning() {
        getManager().startAutoScanning()
    }

    /**
     * Stops the scanning process.
     */
    override fun stopAutoScanning() {
        getManager().stopAutoScanning()
        blockManager.resetForNextUse()
        lineManager.resetForNextUse()
    }

    /**
     * Pauses the scanning process.
     */
    override fun pauseAutoScanning() {
        getManager().pauseAutoScanning()
    }

    /**
     * Resumes the scanning process.
     */
    override fun resumeAutoScanning() {
        getManager().resumeAutoScanning()
    }

    /**
     * Moves the cursor to the next item.
     */
    override fun stepScanningForward() {
        getManager().stepScanningForward()
        if (CursorMode.isBlockMode() && getCurrentBlock() == null) blockManager.showBlocks()
    }

    /**
     * Moves the cursor to the previous item.
     */
    override fun stepScanningBackward() {
        getManager().stepScanningBackward()
        if (CursorMode.isBlockMode() && getCurrentBlock() == null) blockManager.showBlocks()
    }

    /**
     * Performs the selection action.
     */
    override fun performSelectionAction() {
        blockManager.getScanTree().setSpeed(ScanSettings(context).getCursorBlockScanRate())
        getManager().performSelectionAction()
        if (CursorMode.isBlockMode() && getCurrentBlock() == null) blockManager.showBlocks()
    }

    /**
     * Cleans up the cursor manager.
     */
    override fun cleanup() {
        blockManager.cleanup()
        lineManager.cleanup()
    }

    /**
     * Gets the appropriate AccessTechnique based on whether a block is selected or not.
     *
     * @return The appropriate AccessTechniqueInterface.
     */
    private fun getManager(): AccessTechniqueInterface {
        if (CursorMode.isSingleMode()) {
            return lineManager
        }

        return (if (CursorMode.isBlockMode() && getCurrentBlock() == null) {
            blockManager.getScanTree()
        } else {
            lineManager
        })
    }

    fun getCurrentPosition(): Pair<Int, Int> = lineManager.getCurrentPosition()
    fun getCurrentDirection(): ScanDirection = lineManager.getCurrentDirection()
    fun getCurrentBlock(): CursorBlock? = lineManager.getCurrentBlock()
}