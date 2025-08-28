package com.enaboapps.switchify.service.techniques.directcontrol

import android.content.Context
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.utils.ScreenUtils

class DirectControlManager(private val context: Context) : AccessTechniqueInterface {
    private var currentX: Int = ScreenUtils.getWidth(context) / 2
    private var currentY: Int = ScreenUtils.getHeight(context) / 2
    private val settings = DirectControlSettings(context)
    private var dynamicStep: Int = settings.baseStep()
    private var lastMoveAt: Long = 0L
    private val overlay = DirectControlOverlay(context)

    override fun swapScanDirection() { /* no-op for direct control */ }

    override fun startAutoScanning() { /* no-op */ }

    override fun stopScanningAndReset() { overlay.reset() }

    override fun resetUI() { overlay.reset() }

    override fun resetForNextUse() { overlay.reset() }

    override fun pauseAutoScanning() { /* no-op */ }

    override fun resumeAutoScanning() { /* no-op */ }

    override fun stepScanningForward() { stepScanningRight() }

    override fun stepScanningBackward() { stepScanningLeft() }

    override fun performSelectionAction() {
        GesturePoint.x = currentX
        GesturePoint.y = currentY
        SelectionHandler.setSelectAction {
            GestureManager.instance.performTap()
        }
        SelectionHandler.performSelectionAction()
    }

    override fun stepScanningUp() {
        val s = computeStep()
        currentY = (currentY - s).coerceAtLeast(0)
        overlay.showPointer(currentX, currentY)
    }

    override fun stepScanningDown() {
        val h = ScreenUtils.getHeight(context)
        val s = computeStep()
        currentY = (currentY + s).coerceAtMost(h)
        overlay.showPointer(currentX, currentY)
    }

    override fun stepScanningLeft() {
        val s = computeStep()
        currentX = (currentX - s).coerceAtLeast(0)
        overlay.showPointer(currentX, currentY)
    }

    override fun stepScanningRight() {
        val w = ScreenUtils.getWidth(context)
        val s = computeStep()
        currentX = (currentX + s).coerceAtMost(w)
        overlay.showPointer(currentX, currentY)
    }

    private fun computeStep(): Int {
        val now = System.currentTimeMillis()
        val accelWin = settings.accelWindowMs()
        val decayWin = settings.decayWindowMs()
        val base = settings.baseStep()
        val max = settings.maxStep()
        val inc = settings.accelIncrement()

        dynamicStep = when {
            lastMoveAt != 0L && now - lastMoveAt <= accelWin -> (dynamicStep + inc).coerceAtMost(max)
            lastMoveAt != 0L && now - lastMoveAt >= decayWin -> base
            dynamicStep < base -> base
            else -> dynamicStep
        }
        lastMoveAt = now

        val precisionMul = if (settings.precisionEnabled()) settings.precisionMultiplier() else 1f
        return (dynamicStep * precisionMul).toInt().coerceAtLeast(1)
    }

    fun getCurrentPosition(): Pair<Int, Int> = Pair(currentX, currentY)
    fun getCurrentDirection(): ScanDirection = ScanDirection.RIGHT
}
