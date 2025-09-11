package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.menu.MenuManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HeadControlManager(private val context: Context) {
    
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
    private var headControlScanner: HeadControlItemScanner? = null
    private val menuScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeDirection: Direction? = null
    private var heldDirection: Direction? = null
    private var repeatJob: Job? = null

    private enum class Direction { LEFT, RIGHT, UP, DOWN }
    
    
    init {
        // Auto-start head control when manager is created
        Log.d(TAG, "HeadControlManager initialized - auto-starting")
        startHeadControl()
    }

    fun startHeadControl() { 
        Log.d(TAG, "startHeadControl called - showing overlay at $currentX, $currentY")
        showPointerIfAllowed()
    }

    fun stopHeadControl() {
        overlay.reset()
        resetGestureState()
    }

    fun resetToCenter() {
        overlay.reset()
        // Reset to center
        currentX = ScreenUtils.getWidth(context) / 2
        currentY = ScreenUtils.getHeight(context) / 2
        targetX = currentX
        targetY = currentY
    }

    fun pauseHeadControl() { 
        overlay.hidePointer()
        resetGestureState()
    }

    fun resumeHeadControl() { 
        showPointerIfAllowed()
    }

    fun cleanup() {
        overlay.reset()
        resetGestureState()
        repeatJob?.cancel()
        menuScope.cancel()
    }

    fun performSelection() {
        if (isInMenuMode) {
            headControlScanner?.performSelection()
            return
        }
        GesturePoint.x = currentX
        GesturePoint.y = currentY
        SelectionHandler.setSelectAction {
            GestureManager.instance.performTap()
        }
        SelectionHandler.performSelectionAction()
    }

    fun stepUp() {
        val step = settings.baseStep()
        targetY = (targetY - step).coerceAtLeast(minY)
        smoothMovement()
    }

    fun stepDown() {
        val step = settings.baseStep()
        targetY = (targetY + step).coerceAtMost(maxY)
        smoothMovement()
    }

    fun stepLeft() {
        val step = settings.baseStep()
        targetX = (targetX - step).coerceAtLeast(minX)
        smoothMovement()
    }

    fun stepRight() {
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
        val isAbsolute = settings.isAbsoluteMode()
        val pos = if (isAbsolute) {
            updateAbsoluteMode(headRotationX, headRotationY)
        } else {
            updateContinuousMode(headRotationX, headRotationY)
        }

        if (isInMenuMode) {
            handleMenuDirection(headRotationX, headRotationY)
        } else {
            if (isAbsolute) {
                targetX = pos.first.toInt().coerceIn(minX, maxX)
                targetY = pos.second.toInt().coerceIn(minY, maxY)
                smoothMovement()
            } else {
                currentX = pos.first.toInt().coerceIn(minX, maxX)
                currentY = pos.second.toInt().coerceIn(minY, maxY)
                targetX = currentX
                targetY = currentY
                showPointerIfAllowed()
            }
        }
    }
    
    private fun handleMenuDirection(headRotationX: Float, headRotationY: Float) {
        val dir = evaluateDirection(headRotationX, headRotationY)
        heldDirection = dir
        if (dir == null) {
            if (activeDirection != null) {
                repeatJob?.cancel()
                repeatJob = null
                activeDirection = null
            }
            return
        }
        if (dir != activeDirection) {
            repeatJob?.cancel()
            activeDirection = dir
            doStep(dir)
            repeatJob = menuScope.launch {
                val initialDelay = settings.menuRepeatInitialDelay()
                delay(initialDelay)
                while (activeDirection != null && activeDirection == heldDirection) {
                    doStep(dir)
                    val interval = settings.menuRepeatInterval()
                    delay(interval)
                }
            }
        }
    }

    private fun evaluateDirection(headRotationX: Float, headRotationY: Float): Direction? {
        val leftDeadzone = settings.getEffectiveLeftDeadzone()
        val rightDeadzone = settings.getEffectiveRightDeadzone()
        val upDeadzone = settings.getEffectiveUpDeadzone()
        val downDeadzone = settings.getEffectiveDownDeadzone()

        var candidate: Direction? = null
        var maxScore = 0f

        if (headRotationY > rightDeadzone) {
            val score = (headRotationY - rightDeadzone) / (30f - rightDeadzone)
            if (score > maxScore) { maxScore = score; candidate = Direction.RIGHT }
        }
        if (-headRotationY > leftDeadzone) {
            val score = (-headRotationY - leftDeadzone) / (30f - leftDeadzone)
            if (score > maxScore) { maxScore = score; candidate = Direction.LEFT }
        }
        if (headRotationX > downDeadzone) {
            val score = (headRotationX - downDeadzone) / (30f - downDeadzone)
            if (score > maxScore) { maxScore = score; candidate = Direction.DOWN }
        }
        if (-headRotationX > upDeadzone) {
            val score = (-headRotationX - upDeadzone) / (30f - upDeadzone)
            if (score > maxScore) { maxScore = score; candidate = Direction.UP }
        }

        return candidate
    }

    private fun doStep(dir: Direction) {
        when (dir) {
            Direction.LEFT -> headControlScanner?.stepLeft()
            Direction.RIGHT -> headControlScanner?.stepRight()
            Direction.UP -> headControlScanner?.stepUp()
            Direction.DOWN -> headControlScanner?.stepDown()
        }
    }

    private fun updateAbsoluteMode(headRotationX: Float, headRotationY: Float): Pair<Float, Float> {
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
        val desiredX = (screenWidth / 2 + normalizedX * screenWidth / 2 * sensitivity)
        val desiredY = (screenHeight / 2 + normalizedY * screenHeight / 2 * sensitivity)
        return Pair(desiredX, desiredY)
    }
    
    private fun updateContinuousMode(headRotationX: Float, headRotationY: Float): Pair<Float, Float> {
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
            val movementDelta = 8f
            val desiredX = currentX + horizontalMovement * movementDelta
            val desiredY = currentY + verticalMovement * movementDelta
            return Pair(desiredX, desiredY)
        }
        return Pair(currentX.toFloat(), currentY.toFloat())
    }
    
    
    private fun smoothMovement() {
        // Apply smoothing to prevent jittery movement
        val deltaX = (targetX - currentX) * smoothingFactor
        val deltaY = (targetY - currentY) * smoothingFactor
        
        currentX = (currentX + deltaX).toInt().coerceIn(minX, maxX)
        currentY = (currentY + deltaY).toInt().coerceIn(minY, maxY)
        
        showPointerIfAllowed()
    }

    private fun showPointerIfAllowed() {
        if (!isInMenuMode) {
            overlay.showPointer(currentX, currentY)
        }
    }

    fun getCurrentPosition(): Pair<Int, Int> = Pair(currentX, currentY)
    
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
            performSelection()
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
        Log.d(TAG, "setMenuMode called with: $menuMode, current mode: $isInMenuMode")
        if (isInMenuMode != menuMode) {
            Log.d(TAG, "Menu mode changed: $menuMode")
            isInMenuMode = menuMode
            
            if (menuMode) {
                Log.d(TAG, "Entering menu mode - getting current menu view...")
                val menuView = MenuManager.getInstance().getCurrentMenuView()
                Log.d(TAG, "Current menu view: $menuView")
                val nodes = menuView?.getSelectableNodes() ?: emptyList()
                Log.d(TAG, "Got ${nodes.size} selectable nodes from menu view")
                if (headControlScanner == null) {
                    Log.d(TAG, "Creating new HeadControlItemScanner")
                    headControlScanner = HeadControlItemScanner()
                }
                Log.d(TAG, "About to set nodes on scanner...")
                headControlScanner?.setNodes(nodes)
                headControlScanner?.initializeSelectionNear(currentX.toFloat(), currentY.toFloat())
                Log.d(TAG, "Set ${nodes.size} nodes for head control navigation")
                overlay.hidePointer()
            } else {
                headControlScanner?.clear()
                headControlScanner = null
                Log.d(TAG, "Cleared head control nodes")
                showPointerIfAllowed()
                repeatJob?.cancel()
                repeatJob = null
                activeDirection = null
                heldDirection = null
            }
        }
    }
    
    /**
     * Refresh menu nodes when menu changes (e.g., page change)
     */
    fun refreshMenuNodes() {
        if (isInMenuMode) {
            val menuView = MenuManager.getInstance().getCurrentMenuView()
            val nodes = menuView?.getSelectableNodes() ?: emptyList()
            headControlScanner?.setNodes(nodes)
            headControlScanner?.initializeSelectionNear(currentX.toFloat(), currentY.toFloat())
            Log.d(TAG, "Refreshed ${nodes.size} nodes for head control navigation")
        }
    }
}
