package com.enaboapps.switchify.service.techniques.cursor

import android.content.Context
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.gestures.GesturePointListener
import com.enaboapps.switchify.service.scanning.ScanDirection
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
    private val lineManager = CursorLineManager(context)

    init {
        GesturePoint.listener = this
        CursorMode.init(context)
        blockManager.initializeBlocks()
    }

    /**
     * Handles gesture point reselection.
     */
    override fun onGesturePointReselect() {

    }


    /**
     * Sets the block based on the position.
     *
     * @param position The position of the block.
     */
    private fun setBlock(position: Int) {
        val block = blockManager.getBlock(position)
        lineManager.setBlock(block)
        lineManager.startScanning()
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
    override fun startScanning() {
        getManager().startScanning()
    }

    /**
     * Stops the scanning process.
     */
    override fun stopScanning() {
        getManager().stopScanning()
    }

    /**
     * Pauses the scanning process.
     */
    override fun pauseScanning() {
        getManager().pauseScanning()
    }

    /**
     * Resumes the scanning process.
     */
    override fun resumeScanning() {
        getManager().resumeScanning()
    }

    /**
     * Moves the cursor to the next item.
     */
    override fun stepForward() {
        getManager().stepForward()
    }

    /**
     * Moves the cursor to the previous item.
     */
    override fun stepBackward() {
        getManager().stepBackward()
    }

    /**
     * Performs the selection action.
     */
    override fun performSelectionAction() {
        getManager().performSelectionAction()
    }

    /**
     * Cleans up the cursor manager.
     */
    override fun cleanup() {
        blockManager.reset()
        lineManager.cleanup()
    }

    /**
     * Gets the appropriate AccessTechnique based on whether a block is selected or not.
     *
     * @return The appropriate AccessTechniqueInterface.
     */
    private fun getManager(): AccessTechniqueInterface {
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