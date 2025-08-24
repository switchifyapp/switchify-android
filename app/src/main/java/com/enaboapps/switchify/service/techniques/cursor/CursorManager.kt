package com.enaboapps.switchify.service.techniques.cursor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.scanning.ScanDirection
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
class CursorManager(private val context: Context) : AccessTechniqueInterface {
    private val blockManager = CursorBlockManager(context, onBlockSelected = { setBlock(it) })
    private val lineManager = CursorLineManager(context, onPointSelected = {
        handleFinalSelectionPoint(it.x.toInt(), it.y.toInt())
    })

    private val settingsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onSettingsChanged()
        }
    }

    init {
        CursorSettings.init(context)
        blockManager.initializeBlocks()
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            settingsChangedReceiver,
            IntentFilter(CursorSettings.CURSOR_SETTINGS_CHANGED_ACTION),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * Called when the settings have changed.
     */
    private fun onSettingsChanged() {
        resetUI()
        blockManager.initializeBlocks()
    }


    /**
     * Sets the block based on the position.
     *
     * @param position The position of the block.
     */
    private fun setBlock(position: Int) {
        val block = blockManager.getBlock(position)
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
            GestureManager.instance.performTap()
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
    override fun stopScanningAndReset() {
        super.stopScanningAndReset()
        getManager().stopScanningAndReset()
    }

    override fun resetUI() {
        blockManager.resetForNextUse()
        lineManager.resetForNextUse()
    }

    override fun resetForNextUse() {
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
        if (CursorSettings.isBlockMode() && getCurrentBlock() == null) blockManager.showBlocks()
    }

    /**
     * Moves the cursor to the previous item.
     */
    override fun stepScanningBackward() {
        getManager().stepScanningBackward()
        if (CursorSettings.isBlockMode() && getCurrentBlock() == null) blockManager.showBlocks()
    }

    /**
     * Performs the selection action.
     */
    override fun performSelectionAction() {
        blockManager.getScanTree().setSpeed(CursorSettings.getCursorBlockScanRate())
        getManager().performSelectionAction()
        if (CursorSettings.isBlockMode() && getCurrentBlock() == null) blockManager.showBlocks()
    }

    /**
     * Steps up in the cursor scanning (not applicable for cursor mode).
     */
    override fun stepScanningUp() {
        getManager().stepScanningUp()
    }

    /**
     * Steps down in the cursor scanning (not applicable for cursor mode).
     */
    override fun stepScanningDown() {
        getManager().stepScanningDown()
    }

    /**
     * Steps left in the cursor scanning.
     */
    override fun stepScanningLeft() {
        getManager().stepScanningLeft()
    }

    /**
     * Steps right in the cursor scanning.
     */
    override fun stepScanningRight() {
        getManager().stepScanningRight()
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
        if (CursorSettings.isSingleMode()) {
            return lineManager
        }

        return (if (CursorSettings.isBlockMode() && getCurrentBlock() == null) {
            blockManager.getScanTree()
        } else {
            lineManager
        })
    }

    fun getCurrentPosition(): Pair<Int, Int> = lineManager.getCurrentPosition()
    fun getCurrentDirection(): ScanDirection = lineManager.getCurrentDirection()
    fun getCurrentBlock(): CursorBlock? = lineManager.getCurrentBlock()
}