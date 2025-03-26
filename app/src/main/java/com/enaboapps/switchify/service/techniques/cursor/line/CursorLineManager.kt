package com.enaboapps.switchify.service.techniques.cursor.line

import android.content.Context
import android.graphics.Rect
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.scanning.ScanningScheduler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.techniques.cursor.blocks.CursorBlock
import com.enaboapps.switchify.service.utils.ScreenUtils

class CursorLineManager(
    private val context: Context,
    private val lineMovement: Int = 10
) : AccessTechniqueInterface {

    private var scanningScheduler: ScanningScheduler? = null
    private var isScanning = false
    private var currentDirection: ScanDirection = ScanDirection.RIGHT
    private var currentX: Int = 0
    private var currentY: Int = 0
    private var currentBlock: CursorBlock? = null
    private val lineUI = LineUI(context)

    private fun getScreenBounds(): Rect {
        return Rect(0, 0, ScreenUtils.getWidth(context), ScreenUtils.getHeight(context))
    }

    private fun setup() {
        if (scanningScheduler == null) {
            scanningScheduler = ScanningScheduler(context) {
                stepScanning()
            }
        }
    }

    private fun stepScanning() {
        val bounds = currentBlock?.let { block ->
            Rect(block.left, block.top, block.right, block.bottom)
        } ?: getScreenBounds()

        when (currentDirection) {
            ScanDirection.LEFT -> {
                if (currentX > bounds.left + lineMovement) {
                    currentX -= lineMovement
                    lineUI.showXCursorLine(currentX)
                } else {
                    currentDirection = ScanDirection.RIGHT
                }
            }

            ScanDirection.RIGHT -> {
                if (currentX < bounds.right - lineMovement) {
                    currentX += lineMovement
                    lineUI.showXCursorLine(currentX)
                } else {
                    currentDirection = ScanDirection.LEFT
                }
            }

            ScanDirection.UP -> {
                if (currentY > bounds.top + lineMovement) {
                    currentY -= lineMovement
                    lineUI.showYCursorLine(currentY)
                } else {
                    currentDirection = ScanDirection.DOWN
                }
            }

            ScanDirection.DOWN -> {
                if (currentY < bounds.bottom - lineMovement) {
                    currentY += lineMovement
                    lineUI.showYCursorLine(currentY)
                } else {
                    currentDirection = ScanDirection.UP
                }
            }
        }
    }

    override fun startScanning() {
        if (!isScanning) {
            isScanning = true
            setup()
            scanningScheduler?.startScanning()
        }
    }

    override fun stopScanning() {
        if (isScanning) {
            isScanning = false
            scanningScheduler?.stopScanning()
            lineUI.reset()
        }
    }

    override fun pauseScanning() {
        scanningScheduler?.pauseScanning()
    }

    override fun resumeScanning() {
        scanningScheduler?.resumeScanning()
    }

    override fun stepForward() {
        if (!isScanning) {
            startScanning()
        } else {
            stepScanning()
        }
    }

    override fun stepBackward() {
        // Reverse direction
        currentDirection = when (currentDirection) {
            ScanDirection.LEFT -> ScanDirection.RIGHT
            ScanDirection.RIGHT -> ScanDirection.LEFT
            ScanDirection.UP -> ScanDirection.DOWN
            ScanDirection.DOWN -> ScanDirection.UP
        }
    }

    override fun swapScanDirection() {
        currentDirection = when (currentDirection) {
            ScanDirection.LEFT, ScanDirection.RIGHT -> ScanDirection.DOWN
            ScanDirection.UP, ScanDirection.DOWN -> ScanDirection.RIGHT
        }
    }

    override fun performSelectionAction() {
        // TODO: Implement selection action
    }

    override fun cleanup() {
        stopScanning()
        scanningScheduler?.shutdown()
        scanningScheduler = null
    }

    fun setBlock(block: CursorBlock?) {
        currentBlock = block
        if (block != null) {
            currentX = block.left
            currentY = block.top
            lineUI.showXCursorLine(currentX)
        } else {

            currentX = 0
            currentY = 0
            lineUI.reset()
        }
    }

    fun getCurrentPosition(): Pair<Int, Int> = Pair(currentX, currentY)
    fun getCurrentDirection(): ScanDirection = currentDirection
    fun getCurrentBlock(): CursorBlock? = currentBlock
}