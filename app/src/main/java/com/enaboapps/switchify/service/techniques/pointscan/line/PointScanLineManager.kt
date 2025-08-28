package com.enaboapps.switchify.service.techniques.pointscan.line

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanningScheduler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings
import com.enaboapps.switchify.service.techniques.pointscan.blocks.PointScanBlock
import com.enaboapps.switchify.service.techniques.shared.ScanMethodUIConstants
import com.enaboapps.switchify.service.utils.ScreenUtils

class PointScanLineManager(
    private val context: Context,
    private val lineMovement: Int = 10,
    private val onPointSelected: (PointF) -> Unit
) : AccessTechniqueInterface {

    private var scanningScheduler = ScanningScheduler(context) { stepScanning() }
    private var currentDirection: ScanDirection = ScanDirection.RIGHT
    private var currentX: Int = 0
    private var currentY: Int = 0
    private var currentBlock: PointScanBlock? = null
    private val lineUI = LineUI(context)

    private val scanSettings = ScanSettings(context)

    private fun getScreenBounds(): Rect {
        return Rect(0, 0, ScreenUtils.getWidth(context), ScreenUtils.getHeight(context))
    }

    private fun stepScanning() {
        val lineThickness = ScanMethodUIConstants.LINE_THICKNESS
        val bounds = currentBlock?.let { block ->
            Rect(
                block.left + lineThickness,
                block.top + lineThickness,
                block.right - lineThickness,
                block.bottom - lineThickness
            )
        } ?: getScreenBounds()

        when (currentDirection) {
            ScanDirection.LEFT -> {
                if (currentX > bounds.left + lineMovement) {
                    currentX -= lineMovement
                    lineUI.showXCursorLine(currentX)
                } else {
                    currentX = bounds.right
                    lineUI.showXCursorLine(currentX)
                }
            }

            ScanDirection.RIGHT -> {
                if (currentX < bounds.right - lineMovement) {
                    currentX += lineMovement
                    lineUI.showXCursorLine(currentX)
                } else {
                    currentX = bounds.left
                    lineUI.showXCursorLine(currentX)
                }
            }

            ScanDirection.UP -> {
                if (currentY > bounds.top + lineMovement) {
                    currentY -= lineMovement
                    lineUI.showYCursorLine(currentY)
                } else {
                    currentY = bounds.bottom
                    lineUI.showYCursorLine(currentY)
                }
            }

            ScanDirection.DOWN -> {
                if (currentY < bounds.bottom - lineMovement) {
                    currentY += lineMovement
                    lineUI.showYCursorLine(currentY)
                } else {
                    currentY = bounds.top
                    lineUI.showYCursorLine(currentY)
                }
            }
        }
    }

    override fun startAutoScanning() {
        if (scanningScheduler.isScanning() == false && scanSettings.isAutoScanMode()) {
            val rate = PointScanSettings.getFineCursorScanRate()
            scanningScheduler.startScanning(initialDelay = rate, period = rate)
        }
    }

    override fun stopScanningAndReset() {
        super.stopScanningAndReset()
        scanningScheduler.stopScanning()
    }

    override fun resetUI() {
        lineUI.reset()
    }

    override fun pauseAutoScanning() {
        if (scanSettings.isAutoScanMode()) {
            scanningScheduler.pauseScanning()
        }
    }

    override fun resumeAutoScanning() {
        if (scanSettings.isAutoScanMode()) {
            scanningScheduler.resumeScanning()
        }
    }

    override fun stepScanningForward() {
        // If direction is left, swap to right, if up, swap to down
        currentDirection = when (currentDirection) {
            ScanDirection.LEFT -> ScanDirection.RIGHT
            ScanDirection.UP -> ScanDirection.DOWN
            else -> currentDirection
        }
        // Step scanning
        stepScanning()
    }

    override fun stepScanningBackward() {
        // If direction is right, swap to left, if down, swap to up
        currentDirection = when (currentDirection) {
            ScanDirection.RIGHT -> ScanDirection.LEFT
            ScanDirection.DOWN -> ScanDirection.UP
            else -> currentDirection
        }
        // Step scanning
        stepScanning()
    }

    override fun swapScanDirection() {
        currentDirection = when (currentDirection) {
            ScanDirection.LEFT -> ScanDirection.RIGHT
            ScanDirection.RIGHT -> ScanDirection.LEFT
            ScanDirection.UP -> ScanDirection.DOWN
            ScanDirection.DOWN -> ScanDirection.UP
        }
    }

    override fun performSelectionAction() {
        if (scanningScheduler.isScanning() == false && scanSettings.isAutoScanMode()) {
            startAutoScanning()
            lineUI.showXCursorLine(currentX)
            return
        }

        if (currentDirection == ScanDirection.LEFT || currentDirection == ScanDirection.RIGHT) {
            currentDirection = ScanDirection.DOWN
            lineUI.showYCursorLine(currentY)

            return
        }

        onPointSelected(PointF(currentX.toFloat(), currentY.toFloat()))
    }

    override fun stepScanningUp() {
        currentDirection = ScanDirection.UP
        stepScanning()
    }

    override fun stepScanningDown() {
        currentDirection = ScanDirection.DOWN
        stepScanning()
    }

    override fun stepScanningLeft() {
        currentDirection = ScanDirection.LEFT
        stepScanning()
    }

    override fun stepScanningRight() {
        currentDirection = ScanDirection.RIGHT
        stepScanning()
    }

    override fun cleanup() {
        super.cleanup()
        scanningScheduler.shutdown()
    }

    override fun resetForNextUse() {
        scanningScheduler.stopScanning() // can't use stopScanningAndReset() because it will recursively call resetForNextUse()
        lineUI.reset()
        currentBlock = null
        currentX = 0
        currentY = 0
        currentDirection = ScanDirection.RIGHT
    }

    fun setBlock(block: PointScanBlock?) {
        currentBlock = block
        lineUI.setBlock(block)
        if (block != null) {
            currentX = block.left
            currentY = block.top
            lineUI.showXCursorLine(currentX)
        } else {
            currentX = 0
            currentY = 0
            lineUI.showXCursorLine(currentX)
        }
    }

    fun getCurrentPosition(): Pair<Int, Int> = Pair(currentX, currentY)
    fun getCurrentDirection(): ScanDirection = currentDirection
    fun getCurrentBlock(): PointScanBlock? = currentBlock
}
