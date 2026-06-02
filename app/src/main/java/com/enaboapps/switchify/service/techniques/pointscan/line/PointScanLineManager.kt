package com.enaboapps.switchify.service.techniques.pointscan.line

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanVisualConstants
import com.enaboapps.switchify.service.scanning.ScanningScheduler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings
import com.enaboapps.switchify.service.techniques.pointscan.blocks.PointScanBlock
import com.enaboapps.switchify.service.utils.ContinuousLineSpeedUtils
import com.enaboapps.switchify.service.utils.ScreenUtils

class PointScanLineManager(
    private val context: Context,
    private val onPointSelected: (PointF) -> Unit
) : AccessTechniqueInterface {

    companion object {
        private const val MAX_ELAPSED_MS = 250L
    }

    private var scanningScheduler = ScanningScheduler(context) { stepAutoScanning() }
    private var currentDirection: ScanDirection = ScanDirection.RIGHT
    private var currentX: Float = 0f
    private var currentY: Float = 0f
    private var currentBlock: PointScanBlock? = null
    private val lineUI = LineUI(context)
    private var lastUpdateTime = 0L

    private val scanSettings = ScanSettings(context)

    private fun getScreenBounds(): Rect {
        return Rect(0, 0, ScreenUtils.getWidth(context), ScreenUtils.getHeight(context))
    }

    private fun stepAutoScanning() {
        stepScanning(getAutoMovementDelta())
    }

    private fun stepManualScanning() {
        stepScanning(getManualMovementDelta())
    }

    private fun getAutoMovementDelta(): Float {
        val now = SystemClock.elapsedRealtime()
        val elapsedMs = if (lastUpdateTime == 0L) {
            ContinuousLineSpeedUtils.UPDATE_PERIOD_MS
        } else {
            now - lastUpdateTime
        }.coerceIn(1L, MAX_ELAPSED_MS)
        lastUpdateTime = now
        return PointScanSettings.getLineSpeedPxPerSecond(context) * elapsedMs / 1000f
    }

    private fun getManualMovementDelta(): Float {
        return (PointScanSettings.getLineSpeedPxPerSecond(context) * ContinuousLineSpeedUtils.UPDATE_PERIOD_MS / 1000f)
            .coerceAtLeast(1f)
    }

    private fun resetElapsedTime() {
        lastUpdateTime = SystemClock.elapsedRealtime()
    }

    private fun stepScanning(lineMovement: Float) {
        val lineThickness = ScreenUtils.dpToPx(context, ScanVisualConstants.CURSOR_LINE_DP)
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
                    lineUI.showXScanLine(currentX.toInt())
                } else {
                    currentX = bounds.right.toFloat()
                    lineUI.showXScanLine(currentX.toInt())
                }
            }

            ScanDirection.RIGHT -> {
                if (currentX < bounds.right - lineMovement) {
                    currentX += lineMovement
                    lineUI.showXScanLine(currentX.toInt())
                } else {
                    currentX = bounds.left.toFloat()
                    lineUI.showXScanLine(currentX.toInt())
                }
            }

            ScanDirection.UP -> {
                if (currentY > bounds.top + lineMovement) {
                    currentY -= lineMovement
                    lineUI.showYScanLine(currentY.toInt())
                } else {
                    currentY = bounds.bottom.toFloat()
                    lineUI.showYScanLine(currentY.toInt())
                }
            }

            ScanDirection.DOWN -> {
                if (currentY < bounds.bottom - lineMovement) {
                    currentY += lineMovement
                    lineUI.showYScanLine(currentY.toInt())
                } else {
                    currentY = bounds.top.toFloat()
                    lineUI.showYScanLine(currentY.toInt())
                }
            }
        }
    }

    override fun startAutoScanning() {
        if (scanningScheduler.isScanning() == false && scanSettings.isAutoScanMode()) {
            val rate = ContinuousLineSpeedUtils.UPDATE_PERIOD_MS
            resetElapsedTime()
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
            resetElapsedTime()
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
        stepManualScanning()
    }

    override fun stepScanningBackward() {
        // If direction is right, swap to left, if down, swap to up
        currentDirection = when (currentDirection) {
            ScanDirection.RIGHT -> ScanDirection.LEFT
            ScanDirection.DOWN -> ScanDirection.UP
            else -> currentDirection
        }
        // Step scanning
        stepManualScanning()
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
            lineUI.showXScanLine(currentX.toInt())
            return
        }

        if (currentDirection == ScanDirection.LEFT || currentDirection == ScanDirection.RIGHT) {
            currentDirection = ScanDirection.DOWN
            lineUI.showYScanLine(currentY.toInt())

            return
        }

        onPointSelected(PointF(currentX.toFloat(), currentY.toFloat()))
    }

    override fun stepScanningUp() {
        currentDirection = ScanDirection.UP
        stepManualScanning()
    }

    override fun stepScanningDown() {
        currentDirection = ScanDirection.DOWN
        stepManualScanning()
    }

    override fun stepScanningLeft() {
        currentDirection = ScanDirection.LEFT
        stepManualScanning()
    }

    override fun stepScanningRight() {
        currentDirection = ScanDirection.RIGHT
        stepManualScanning()
    }

    override fun cleanup() {
        super.cleanup()
        scanningScheduler.shutdown()
    }

    override fun resetForNextUse() {
        scanningScheduler.stopScanning() // can't use stopScanningAndReset() because it will recursively call resetForNextUse()
        lineUI.reset()
        currentBlock = null
        currentX = 0f
        currentY = 0f
        currentDirection = ScanDirection.RIGHT
        lastUpdateTime = 0L
    }

    fun setBlock(block: PointScanBlock?) {
        currentBlock = block
        lineUI.setBlock(block)
        if (block != null) {
            currentX = block.left.toFloat()
            currentY = block.top.toFloat()
            lineUI.showXScanLine(currentX.toInt())
        } else {
            currentX = 0f
            currentY = 0f
            lineUI.showXScanLine(currentX.toInt())
        }
    }

    fun getCurrentPosition(): Pair<Int, Int> = Pair(currentX.toInt(), currentY.toInt())
    fun getCurrentDirection(): ScanDirection = currentDirection
    fun getCurrentBlock(): PointScanBlock? = currentBlock
}
