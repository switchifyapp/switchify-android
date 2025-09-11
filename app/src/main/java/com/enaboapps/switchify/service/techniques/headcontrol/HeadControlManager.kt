package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.scanning.tree.SpatialNavigator
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
    
    // Gesture selection state
    private var isGestureActive = false
    private var gestureStartTime = 0L
    private var currentActiveGesture: String? = null
    
    // Menu navigation state
    private var isInMenuMode = false
    private var currentMenuScanTree: ScanTree? = null
    private var lastMenuTreeIndex = 0
    private var lastMenuNodeIndex = 0
    
    
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
        resetGestureState()
    }

    override fun resumeAutoScanning() { 
        overlay.showPointer(currentX, currentY)
    }

    override fun cleanup() {
        super.cleanup()
        overlay.reset()
        resetGestureState()
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
        if (isInMenuMode) {
            updateMenuNavigation(headRotationX, headRotationY)
        } else if (settings.isAbsoluteMode()) {
            updateAbsoluteMode(headRotationX, headRotationY)
        } else {
            updateContinuousMode(headRotationX, headRotationY)
        }
    }
    
    private fun updateAbsoluteMode(headRotationX: Float, headRotationY: Float) {
        val sensitivity = settings.sensitivity()
        val leftDeadzone = settings.getEffectiveLeftDeadzone()
        val rightDeadzone = settings.getEffectiveRightDeadzone()
        val upDeadzone = settings.getEffectiveUpDeadzone()
        val downDeadzone = settings.getEffectiveDownDeadzone()
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Absolute mode - X: $headRotationX, Y: $headRotationY, sensitivity: $sensitivity, leftDZ: $leftDeadzone, rightDZ: $rightDeadzone, upDZ: $upDeadzone, downDZ: $downDeadzone")
        }
        
        // Apply directional deadzones - ignore small head movements based on direction
        val adjustedX = if (headRotationY > 0 && headRotationY > rightDeadzone) {
            headRotationY
        } else if (headRotationY < 0 && kotlin.math.abs(headRotationY) > leftDeadzone) {
            headRotationY
        } else {
            0f
        }
        
        val adjustedY = if (headRotationX > 0 && headRotationX > downDeadzone) {
            headRotationX
        } else if (headRotationX < 0 && kotlin.math.abs(headRotationX) > upDeadzone) {
            headRotationX
        } else {
            0f
        }
        
        // Convert head rotation to screen coordinates
        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)
        
        // Map head rotation to screen position
        val headRotationRange = 30f
        
        val normalizedX = (adjustedX / headRotationRange).coerceIn(-1f, 1f)
        val normalizedY = (adjustedY / headRotationRange).coerceIn(-1f, 1f)
        
        // Convert to screen coordinates with sensitivity adjustment
        targetX = (screenWidth / 2 + normalizedX * screenWidth / 2 * sensitivity).toInt()
            .coerceIn(minX, maxX)
        targetY = (screenHeight / 2 + normalizedY * screenHeight / 2 * sensitivity).toInt()
            .coerceIn(minY, maxY)
        
        smoothMovement()
    }
    
    private fun updateContinuousMode(headRotationX: Float, headRotationY: Float) {
        val leftDeadzone = settings.getEffectiveLeftDeadzone()
        val rightDeadzone = settings.getEffectiveRightDeadzone()
        val upDeadzone = settings.getEffectiveUpDeadzone()
        val downDeadzone = settings.getEffectiveDownDeadzone()
        val movementSpeed = settings.movementSpeed()
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Continuous mode - X: $headRotationX, Y: $headRotationY, leftDZ: $leftDeadzone, rightDZ: $rightDeadzone, upDZ: $upDeadzone, downDZ: $downDeadzone, speed: $movementSpeed")
        }
        
        // Calculate horizontal movement with separate left/right thresholds
        val horizontalMovement = if (headRotationY > 0 && headRotationY > rightDeadzone) {
            // Moving right
            val normalizedRotation = (headRotationY - rightDeadzone) / (30f - rightDeadzone)
            normalizedRotation.coerceIn(0f, 1f) * movementSpeed
        } else if (headRotationY < 0 && kotlin.math.abs(headRotationY) > leftDeadzone) {
            // Moving left
            val normalizedRotation = (kotlin.math.abs(headRotationY) - leftDeadzone) / (30f - leftDeadzone)
            -(normalizedRotation.coerceIn(0f, 1f) * movementSpeed)
        } else 0f
        
        // Calculate vertical movement with separate up/down thresholds
        val verticalMovement = if (headRotationX > 0 && headRotationX > downDeadzone) {
            // Moving down
            val normalizedRotation = (headRotationX - downDeadzone) / (30f - downDeadzone)
            normalizedRotation.coerceIn(0f, 1f) * movementSpeed
        } else if (headRotationX < 0 && kotlin.math.abs(headRotationX) > upDeadzone) {
            // Moving up
            val normalizedRotation = (kotlin.math.abs(headRotationX) - upDeadzone) / (30f - upDeadzone)
            -(normalizedRotation.coerceIn(0f, 1f) * movementSpeed)
        } else 0f
        
        // Apply movement immediately
        if (horizontalMovement != 0f || verticalMovement != 0f) {
            // Calculate movement delta based on current frame
            val movementDelta = 8f // Base movement per update
            
            targetX = (currentX + horizontalMovement * movementDelta).toInt()
                .coerceIn(minX, maxX)
            targetY = (currentY + verticalMovement * movementDelta).toInt()
                .coerceIn(minY, maxY)
            
            // Apply movement immediately
            currentX = targetX
            currentY = targetY
            
            overlay.showPointer(currentX, currentY)
        }
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
    
    /**
     * Handles gesture detection for head control selection
     */
    fun processGesture(gestureId: String, isGestureStarting: Boolean) {
        if (!settings.isGestureSelectionEnabled()) {
            return
        }
        
        val selectedGesture = settings.selectGesture()
        
        // Only process the gesture that matches our selected gesture
        if (gestureId != selectedGesture) {
            return
        }
        
        if (isGestureStarting && !isGestureActive) {
            Log.d(TAG, "Gesture started: $gestureId")
            gestureStarted(gestureId)
        } else if (!isGestureStarting && isGestureActive && currentActiveGesture == gestureId) {
            Log.d(TAG, "Gesture ended: $gestureId")
            gestureEnded(gestureId)
        }
    }
    
    private fun gestureStarted(gestureId: String) {
        isGestureActive = true
        gestureStartTime = System.currentTimeMillis()
        currentActiveGesture = gestureId
        Log.d(TAG, "Started tracking gesture: $gestureId")
    }
    
    private fun gestureEnded(gestureId: String) {
        if (!isGestureActive || currentActiveGesture != gestureId) {
            return
        }
        
        val duration = System.currentTimeMillis() - gestureStartTime
        val requiredHoldTime = settings.gestureHoldTime()
        
        Log.d(TAG, "Gesture $gestureId held for ${duration}ms (required: ${requiredHoldTime}ms)")
        
        if (duration >= requiredHoldTime) {
            Log.i(TAG, "Gesture selection triggered by $gestureId")
            performSelectionAction()
        } else {
            Log.d(TAG, "Gesture $gestureId not held long enough")
        }
        
        // Reset gesture state
        isGestureActive = false
        gestureStartTime = 0L
        currentActiveGesture = null
    }
    
    /**
     * Resets gesture state (called when pausing or stopping head control)
     */
    fun resetGestureState() {
        isGestureActive = false
        gestureStartTime = 0L
        currentActiveGesture = null
        Log.d(TAG, "Gesture state reset")
    }
    
    /**
     * Set whether head control is in menu mode
     * @param menuMode true if navigating menus, false for screen navigation
     */
    fun setMenuMode(menuMode: Boolean) {
        if (isInMenuMode != menuMode) {
            Log.d(TAG, "Menu mode changed: $menuMode")
            isInMenuMode = menuMode
            
            if (menuMode) {
                // Get the current menu's scan tree
                val menuHierarchy = MenuManager.getInstance().menuHierarchy
                currentMenuScanTree = menuHierarchy?.getTopMenu()?.scanTree
                Log.d(TAG, "Acquired menu scan tree: ${currentMenuScanTree != null}")
            } else {
                currentMenuScanTree = null
                Log.d(TAG, "Cleared menu scan tree")
            }
        }
    }
    
    /**
     * Handle head movement for menu navigation using spatial positioning
     */
    private fun updateMenuNavigation(headRotationX: Float, headRotationY: Float) {
        // Update cursor position for visual feedback
        if (settings.isAbsoluteMode()) {
            updateAbsoluteMode(headRotationX, headRotationY)
        } else {
            updateContinuousMode(headRotationX, headRotationY)
        }
        
        // If we have a menu scan tree, use spatial navigation
        currentMenuScanTree?.let { scanTree ->
            navigateMenuSpatially(scanTree)
        }
    }
    
    /**
     * Navigate menu items based on current head position using SpatialNavigator
     */
    private fun navigateMenuSpatially(scanTree: ScanTree) {
        try {
            // Get the current scan tree items
            val treeItems = scanTree.getTree()
            if (treeItems.isEmpty()) return
            
            // Create spatial navigator
            val spatialNavigator = SpatialNavigator(treeItems)
            
            // Find the closest menu item to current cursor position
            var closestTreeIndex = 0
            var closestNodeIndex = 0
            var minDistance = Float.MAX_VALUE
            
            treeItems.forEachIndexed { treeIndex, treeItem ->
                treeItem.children.forEachIndexed { nodeIndex, node ->
                    val nodeCenterX = node.getLeft() + node.getWidth() / 2
                    val nodeCenterY = node.getTop() + node.getHeight() / 2
                    
                    val distance = kotlin.math.sqrt(
                        ((currentX - nodeCenterX) * (currentX - nodeCenterX) +
                         (currentY - nodeCenterY) * (currentY - nodeCenterY)).toFloat()
                    )
                    
                    if (distance < minDistance) {
                        minDistance = distance
                        closestTreeIndex = treeIndex
                        closestNodeIndex = nodeIndex
                    }
                }
            }
            
            // Only update if the closest item has changed
            if (closestTreeIndex != lastMenuTreeIndex || closestNodeIndex != lastMenuNodeIndex) {
                lastMenuTreeIndex = closestTreeIndex
                lastMenuNodeIndex = closestNodeIndex
                
                // Update the scan tree to highlight the closest item
                scanTree.setSpatialPosition(closestTreeIndex, closestNodeIndex)
                
                Log.d(TAG, "Menu navigation updated: tree=$closestTreeIndex, node=$closestNodeIndex")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in menu spatial navigation", e)
        }
    }
}