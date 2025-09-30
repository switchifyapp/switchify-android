package com.enaboapps.switchify.service.techniques.pointscan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.techniques.pointscan.blocks.PointScanBlock
import com.enaboapps.switchify.service.techniques.pointscan.blocks.PointScanBlockManager
import com.enaboapps.switchify.service.techniques.pointscan.line.PointScanLineManager

/**
 * PointScanManager manages point scanning movement, quadrants, and selection.
 *
 * @param context The application context.
 */
class PointScanManager(private val context: Context) : AccessTechniqueInterface {
    private val blockManager = PointScanBlockManager(context, onBlockSelected = { setBlock(it) })
    private val lineManager = PointScanLineManager(context, onPointSelected = {
        handleFinalSelectionPoint(it.x.toInt(), it.y.toInt())
    })

    private val settingsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onSettingsChanged()
        }
    }

    init {
        PointScanSettings.init(context)
        blockManager.initializeBlocks()
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            settingsChangedReceiver,
            IntentFilter(PointScanSettings.CURSOR_SETTINGS_CHANGED_ACTION),
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
            GestureManager.instance.performTap(overrideFingerMode = com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE)
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
     * Moves the scan to the next position.
     */
    override fun stepScanningForward() {
        getManager().stepScanningForward()
        if (PointScanSettings.isBlockMode() && getCurrentBlock() == null) blockManager.showBlocks()
    }

    /**
     * Moves the scan to the previous position.
     */
    override fun stepScanningBackward() {
        getManager().stepScanningBackward()
        if (PointScanSettings.isBlockMode() && getCurrentBlock() == null) blockManager.showBlocks()
    }

    /**
     * Performs the selection action.
     */
    override fun performSelectionAction() {
        blockManager.getScanTree().setSpeed(PointScanSettings.getCursorBlockScanRate())
        getManager().performSelectionAction()
        if (PointScanSettings.isBlockMode() && getCurrentBlock() == null) blockManager.showBlocks()
    }

    /**
     * Steps up in point scan.
     */
    override fun stepScanningUp() {
        getManager().stepScanningUp()
    }

    /**
     * Steps down in point scan.
     */
    override fun stepScanningDown() {
        getManager().stepScanningDown()
    }

    /**
     * Steps left in point scan.
     */
    override fun stepScanningLeft() {
        getManager().stepScanningLeft()
    }

    /**
     * Steps right in point scan.
     */
    override fun stepScanningRight() {
        getManager().stepScanningRight()
    }

    /**
     * Cleans up the point scan manager.
     */
    override fun cleanup() {
        super.cleanup()
        try {
            context.unregisterReceiver(settingsChangedReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered or already unregistered
        }
        blockManager.cleanup()
        lineManager.cleanup()
    }

    /**
     * Gets the appropriate AccessTechnique based on whether a block is selected or not.
     *
     * @return The appropriate AccessTechniqueInterface.
     */
    private fun getManager(): AccessTechniqueInterface {
        if (PointScanSettings.isSingleMode()) {
            return lineManager
        }

        return (if (PointScanSettings.isBlockMode() && getCurrentBlock() == null) {
            blockManager.getScanTree()
        } else {
            lineManager
        })
    }

    fun getCurrentPosition(): Pair<Int, Int> = lineManager.getCurrentPosition()
    fun getCurrentDirection(): ScanDirection = lineManager.getCurrentDirection()
    fun getCurrentBlock(): PointScanBlock? = lineManager.getCurrentBlock()
}
