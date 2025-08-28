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
    private val step: Int = 20

    override fun swapScanDirection() { /* no-op for direct control */ }

    override fun startAutoScanning() { /* no-op */ }

    override fun stopScanningAndReset() { /* no-op */ }

    override fun resetUI() { /* no-op */ }

    override fun resetForNextUse() { /* no-op */ }

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
        currentY = (currentY - step).coerceAtLeast(0)
    }

    override fun stepScanningDown() {
        val h = ScreenUtils.getHeight(context)
        currentY = (currentY + step).coerceAtMost(h)
    }

    override fun stepScanningLeft() {
        currentX = (currentX - step).coerceAtLeast(0)
    }

    override fun stepScanningRight() {
        val w = ScreenUtils.getWidth(context)
        currentX = (currentX + step).coerceAtMost(w)
    }

    fun getCurrentPosition(): Pair<Int, Int> = Pair(currentX, currentY)
    fun getCurrentDirection(): ScanDirection = ScanDirection.RIGHT
}

