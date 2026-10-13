package com.enaboapps.switchify.service.techniques.radar

import android.content.Context
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.gestures.placement.FingerMode
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.techniques.blocks.BlockManager
import com.enaboapps.switchify.service.techniques.blocks.ScanBlock
import com.enaboapps.switchify.service.techniques.radar.sweep.RadarSweepManager

/**
 * RadarManager coordinates the optional block grid and the radar sweep. In
 * radar-only mode the sweep covers the whole screen. In grid + radar mode the
 * blocks are scanned first and the sweep then runs inside the chosen block.
 */
class RadarManager(private val context: Context) : AccessTechniqueInterface {
    private val blockManager = BlockManager(
        context,
        gridSizeProvider = { RadarSettings.getBlockCount() },
        scanRateProvider = { RadarSettings.getBlockScanRate() },
        onBlockSelected = { setBlock(it) }
    )
    private val sweepManager = RadarSweepManager(context, onPointSelected = {
        handleFinalSelectionPoint(it.x.toInt(), it.y.toInt())
    })

    init {
        RadarSettings.init(context)
        blockManager.initializeBlocks()
    }

    internal fun refreshStructure() {
        val shouldResume = blockManager.getScanTree().isAutoScanning() || sweepManager.isAutoScanning()
        blockManager.resetForNextUse()
        sweepManager.resetForNextUse()
        blockManager.initializeBlocks()
        if (shouldResume) {
            startAutoScanning()
        }
    }

    internal fun refreshBlockTiming() {
        blockManager.refreshTiming()
    }

    private fun setBlock(position: Int) {
        val block = blockManager.getBlock(position)
        blockManager.enterRefinePhase()
        sweepManager.setBlock(block)
        sweepManager.startAutoScanning()
    }

    private fun handleFinalSelectionPoint(x: Int, y: Int) {
        GesturePoint.x = x
        GesturePoint.y = y
        SelectionHandler.setSelectAction {
            GestureManager.instance.performTap(overrideFingerMode = FingerMode.ONE)
        }
        SelectionHandler.setStartScanningAction { startAutoScanning() }
        SelectionHandler.performSelectionAction()
        blockManager.resetForNextUse()
        sweepManager.resetForNextUse()
    }

    override fun swapScanDirection() {
        getManager().swapScanDirection()
    }

    override fun startAutoScanning() {
        if (RadarSettings.isBlockMode() && !blockManager.isInitialized()) {
            return
        }
        getManager().startAutoScanning()
    }

    override fun stopScanningAndReset() {
        super.stopScanningAndReset()
        getManager().stopScanningAndReset()
    }

    override fun resetUI() {
        blockManager.resetForNextUse()
        sweepManager.resetForNextUse()
    }

    override fun resetForNextUse() {
        blockManager.resetForNextUse()
        sweepManager.resetForNextUse()
    }

    override fun pauseAutoScanning() {
        getManager().pauseAutoScanning()
    }

    override fun resumeAutoScanning() {
        getManager().resumeAutoScanning()
    }

    override fun stepScanningForward() {
        getManager().stepScanningForward()
    }

    override fun stepScanningBackward() {
        getManager().stepScanningBackward()
    }

    override fun performSelectionAction() {
        blockManager.refreshTiming()
        getManager().performSelectionAction()
    }

    override fun stepScanningUp() {
        getManager().stepScanningUp()
    }

    override fun stepScanningDown() {
        getManager().stepScanningDown()
    }

    override fun stepScanningLeft() {
        getManager().stepScanningLeft()
    }

    override fun stepScanningRight() {
        getManager().stepScanningRight()
    }

    override fun cleanup() {
        super.cleanup()
        blockManager.cleanup()
        sweepManager.cleanup()
    }

    private fun getManager(): AccessTechniqueInterface {
        if (RadarSettings.isSingleMode()) {
            return sweepManager
        }

        return if (getCurrentBlock() == null) {
            blockManager.getScanTree()
        } else {
            sweepManager
        }
    }

    fun getCurrentBlock(): ScanBlock? = sweepManager.getCurrentBlock()
}
