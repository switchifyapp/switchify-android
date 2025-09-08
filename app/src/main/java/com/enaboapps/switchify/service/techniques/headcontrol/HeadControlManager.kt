package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.utils.ScreenUtils
import android.util.Log

class HeadControlManager(private val context: Context) : AccessTechniqueInterface {
    
    companion object {
        private const val TAG = "HeadControlManager"
    }
    private var currentX: Int = ScreenUtils.getWidth(context) / 2
    private var currentY: Int = ScreenUtils.getHeight(context) / 2
    private val settings = HeadControlSettings(context)
    private val overlay = HeadControlOverlay(context)
    
    // Head movement smoothing
    private var targetX: Int = currentX
    private var targetY: Int = currentY
    private val smoothingFactor = 0.3f // 0 = no smoothing, 1 = instant
    
    // Movement bounds with padding
    private val screenPadding = 50
    private val minX = screenPadding
    private val maxX = ScreenUtils.getWidth(context) - screenPadding
    private val minY = screenPadding
    private val maxY = ScreenUtils.getHeight(context) - screenPadding
    
    init {
        // Auto-start head control when manager is created
        Log.d(TAG, "HeadControlManager initialized - auto-starting")
        startAutoScanning()
    }

    override fun swapScanDirection() { /* no-op for head control */ }

    override fun startAutoScanning() { 
        Log.d(TAG, "startAutoScanning called - showing overlay at $currentX, $currentY")
        overlay.showPointer(currentX, currentY)
    }

    override fun stopScanningAndReset() {
        overlay.reset()
    }

    override fun resetUI() {
        overlay.reset()
    }

    override fun resetForNextUse() {
        overlay.reset()
        // Reset to center
        currentX = ScreenUtils.getWidth(context) / 2
        currentY = ScreenUtils.getHeight(context) / 2
        targetX = currentX
        targetY = currentY
    }

    override fun pauseAutoScanning() { 
        overlay.hidePointer()
    }

    override fun resumeAutoScanning() { 
        overlay.showPointer(currentX, currentY)
    }

    override fun cleanup() {
        super.cleanup()
        overlay.reset()
    }

    override fun stepScanningForward() {
        stepScanningRight()
    }

    override fun stepScanningBackward() {
        stepScanningLeft()
    }

    override fun performSelectionAction() {
        GesturePoint.x = currentX
        GesturePoint.y = currentY
        SelectionHandler.setSelectAction {
            GestureManager.instance.performTap()
        }
        SelectionHandler.performSelectionAction()
    }

    override fun stepScanningUp() {
        val step = settings.baseStep()
        targetY = (targetY - step).coerceAtLeast(minY)
        smoothMovement()
    }

    override fun stepScanningDown() {
        val step = settings.baseStep()
        targetY = (targetY + step).coerceAtMost(maxY)
        smoothMovement()
    }

    override fun stepScanningLeft() {
        val step = settings.baseStep()
        targetX = (targetX - step).coerceAtLeast(minX)
        smoothMovement()
    }

    override fun stepScanningRight() {
        val step = settings.baseStep()
        targetX = (targetX + step).coerceAtMost(maxX)
        smoothMovement()
    }

    /**
     * Updates cursor position based on head rotation values
     * @param headRotationX Pitch rotation (negative = up, positive = down)
     * @param headRotationY Yaw rotation (negative = left, positive = right)
     */
    fun updateHeadPosition(headRotationX: Float, headRotationY: Float) {
        
        val sensitivity = settings.sensitivity()
        val deadzone = settings.deadzone()
        
        Log.d(TAG, "Head rotation - X: $headRotationX, Y: $headRotationY, sensitivity: $sensitivity, deadzone: $deadzone")
        
        // Apply deadzone - ignore small head movements
        val adjustedX = if (kotlin.math.abs(headRotationY) > deadzone) headRotationY else 0f
        val adjustedY = if (kotlin.math.abs(headRotationX) > deadzone) headRotationX else 0f
        
        Log.d(TAG, "Adjusted - X: $adjustedX, Y: $adjustedY")
        
        // Convert head rotation to screen coordinates
        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)
        
        // Map head rotation to screen position
        // Assume head rotation range is approximately -30 to +30 degrees
        val headRotationRange = 30f
        
        val normalizedX = (adjustedX / headRotationRange).coerceIn(-1f, 1f)
        val normalizedY = (adjustedY / headRotationRange).coerceIn(-1f, 1f)
        
        // Convert to screen coordinates with sensitivity adjustment
        targetX = (screenWidth / 2 + normalizedX * screenWidth / 2 * sensitivity).toInt()
            .coerceIn(minX, maxX)
        targetY = (screenHeight / 2 + normalizedY * screenHeight / 2 * sensitivity).toInt()
            .coerceIn(minY, maxY)
        
        Log.d(TAG, "Target position - X: $targetX, Y: $targetY (screen: ${screenWidth}x${screenHeight})")
        
        smoothMovement()
    }
    
    private fun smoothMovement() {
        // Apply smoothing to prevent jittery movement
        val deltaX = (targetX - currentX) * smoothingFactor
        val deltaY = (targetY - currentY) * smoothingFactor
        
        currentX = (currentX + deltaX).toInt().coerceIn(minX, maxX)
        currentY = (currentY + deltaY).toInt().coerceIn(minY, maxY)
        
        overlay.showPointer(currentX, currentY)
    }

    fun getCurrentPosition(): Pair<Int, Int> = Pair(currentX, currentY)
    fun getCurrentDirection(): ScanDirection = ScanDirection.RIGHT
}