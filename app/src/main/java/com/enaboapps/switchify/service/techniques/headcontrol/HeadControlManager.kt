package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.MenuStateObserver
import com.enaboapps.switchify.service.menu.MenuView
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HeadControlManager(private val context: Context) : MenuStateObserver {
    
    companion object {
        private const val TAG = "HeadControlManager"
        private const val SMOOTHING_FACTOR = 0.3f
        private const val SCREEN_PADDING = 50
        private const val HEAD_ROTATION_RANGE = 30f
        private const val MOVEMENT_DELTA = 8f
        private const val MENU_VALIDATION_INTERVAL = 1000L // Check every second
        private const val MAX_MENU_IDLE_TIME = 30000L // 30 seconds max idle in menu
    }
    private var currentX: Int = ScreenUtils.getWidth(context) / 2
    private var currentY: Int = ScreenUtils.getHeight(context) / 2
    private val settings = HeadControlSettings(context)
    private val overlay = HeadControlOverlay(context)
    
    // Movement bounds with padding
    private val minX = SCREEN_PADDING
    private val maxX = ScreenUtils.getWidth(context) - SCREEN_PADDING
    private val minY = SCREEN_PADDING
    private val maxY = ScreenUtils.getHeight(context) - SCREEN_PADDING
    
    // Gesture selection state
    private var isGestureActive = false
    private var gestureStartTime = 0L
    private var currentActiveGesture: String? = null
    
    // Menu navigation state - now managed via observer pattern
    private var headControlScanner: HeadControlItemScanner? = null
    private val menuScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeDirection: Direction? = null
    private var heldDirection: Direction? = null
    private var repeatJob: Job? = null

    private enum class Direction { LEFT, RIGHT, UP, DOWN }
    
    
    init {
        startHeadControl()
        // Register as menu state observer
        MenuManager.getInstance().registerMenuStateObserver(this)
    }

    fun startHeadControl() { 
        showPointerIfAllowed()
    }

    fun cleanup() {
        // Unregister from menu state observer
        MenuManager.getInstance().unregisterMenuStateObserver(this)
        overlay.reset()
        resetGestureState()
        repeatJob?.cancel()
        menuScope.cancel()
    }

    fun performSelection() {
        if (isInMenuMode()) {
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

    /**
     * Check if currently in menu mode by checking if scanner is active
     */
    private fun isInMenuMode(): Boolean {
        return headControlScanner != null
    }

    /**
     * Updates cursor position based on head rotation values
     * @param headRotationX Pitch rotation (negative = up, positive = down)
     * @param headRotationY Yaw rotation (negative = left, positive = right)
     */
    fun updateHeadPosition(headRotationX: Float, headRotationY: Float) {
        if (isInMenuMode()) {
            handleMenuDirection(headRotationX, headRotationY)
        } else {
            // Calculate head movement
            val pos = calculateMovement(headRotationX, headRotationY)
            currentX = pos.first.toInt().coerceIn(minX, maxX)
            currentY = pos.second.toInt().coerceIn(minY, maxY)
            showPointerIfAllowed()
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
            val score = (headRotationY - rightDeadzone) / (HEAD_ROTATION_RANGE - rightDeadzone)
            if (score > maxScore) { maxScore = score; candidate = Direction.RIGHT }
        }
        if (-headRotationY > leftDeadzone) {
            val score = (-headRotationY - leftDeadzone) / (HEAD_ROTATION_RANGE - leftDeadzone)
            if (score > maxScore) { maxScore = score; candidate = Direction.LEFT }
        }
        if (headRotationX > downDeadzone) {
            val score = (headRotationX - downDeadzone) / (HEAD_ROTATION_RANGE - downDeadzone)
            if (score > maxScore) { maxScore = score; candidate = Direction.DOWN }
        }
        if (-headRotationX > upDeadzone) {
            val score = (-headRotationX - upDeadzone) / (HEAD_ROTATION_RANGE - upDeadzone)
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
        
        val normalizedX = (adjustedX / HEAD_ROTATION_RANGE).coerceIn(-1f, 1f)
        val normalizedY = (adjustedY / HEAD_ROTATION_RANGE).coerceIn(-1f, 1f)
        
        // Convert to screen coordinates with sensitivity adjustment
        val desiredX = (screenWidth / 2 + normalizedX * screenWidth / 2 * sensitivity)
        val desiredY = (screenHeight / 2 + normalizedY * screenHeight / 2 * sensitivity)
        return Pair(desiredX, desiredY)
    }
    
    private fun calculateMovement(headRotationX: Float, headRotationY: Float): Pair<Float, Float> {
        val leftDeadzone = settings.getEffectiveLeftDeadzone()
        val rightDeadzone = settings.getEffectiveRightDeadzone()
        val upDeadzone = settings.getEffectiveUpDeadzone()
        val downDeadzone = settings.getEffectiveDownDeadzone()
        val movementSpeed = settings.movementSpeed()
        
        
        // Calculate horizontal movement with separate left/right thresholds
        val horizontalMovement = if (headRotationY > 0 && headRotationY > rightDeadzone) {
            // Moving right
            val normalizedRotation = (headRotationY - rightDeadzone) / (HEAD_ROTATION_RANGE - rightDeadzone)
            normalizedRotation.coerceIn(0f, 1f) * movementSpeed
        } else if (headRotationY < 0 && kotlin.math.abs(headRotationY) > leftDeadzone) {
            // Moving left
            val normalizedRotation = (kotlin.math.abs(headRotationY) - leftDeadzone) / (HEAD_ROTATION_RANGE - leftDeadzone)
            -(normalizedRotation.coerceIn(0f, 1f) * movementSpeed)
        } else 0f
        
        // Calculate vertical movement with separate up/down thresholds
        val verticalMovement = if (headRotationX > 0 && headRotationX > downDeadzone) {
            // Moving down
            val normalizedRotation = (headRotationX - downDeadzone) / (HEAD_ROTATION_RANGE - downDeadzone)
            normalizedRotation.coerceIn(0f, 1f) * movementSpeed
        } else if (headRotationX < 0 && kotlin.math.abs(headRotationX) > upDeadzone) {
            // Moving up
            val normalizedRotation = (kotlin.math.abs(headRotationX) - upDeadzone) / (HEAD_ROTATION_RANGE - upDeadzone)
            -(normalizedRotation.coerceIn(0f, 1f) * movementSpeed)
        } else 0f
        
        // Apply movement immediately
        if (horizontalMovement != 0f || verticalMovement != 0f) {
            val desiredX = currentX + horizontalMovement * MOVEMENT_DELTA
            val desiredY = currentY + verticalMovement * MOVEMENT_DELTA
            return Pair(desiredX, desiredY)
        }
        return Pair(currentX.toFloat(), currentY.toFloat())
    }
    
    

    private fun showPointerIfAllowed() {
        if (!isInMenuMode()) {
            overlay.showPointer(currentX, currentY)
        }
    }
    
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
            gestureStarted(gestureId)
        } else if (!isGestureStarting && isGestureActive && currentActiveGesture == gestureId) {
            gestureEnded(gestureId)
        }
    }
    
    private fun gestureStarted(gestureId: String) {
        isGestureActive = true
        gestureStartTime = System.currentTimeMillis()
        currentActiveGesture = gestureId
    }
    
    private fun gestureEnded(gestureId: String) {
        if (!isGestureActive || currentActiveGesture != gestureId) {
            return
        }
        
        val duration = System.currentTimeMillis() - gestureStartTime
        val requiredHoldTime = settings.gestureHoldTime()
        
        if (duration >= requiredHoldTime) {
            performSelection()
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
    }
    
    // MenuStateObserver implementation
    override fun onMenuOpened(menuView: MenuView) {
        Log.d(TAG, "Menu opened, entering menu navigation mode")
        val nodes = menuView.getSelectableNodes()
        
        if (nodes.isEmpty()) {
            Log.w(TAG, "Menu opened but has no selectable nodes")
            return
        }
        
        // Setup menu navigation
        if (headControlScanner == null) {
            headControlScanner = HeadControlItemScanner()
        }
        headControlScanner?.setNodes(nodes)
        headControlScanner?.initializeSelectionNear(currentX.toFloat(), currentY.toFloat())
        overlay.hidePointer()
    }
    
    override fun onMenuClosed(menuView: MenuView) {
        Log.d(TAG, "Menu closed")
        // Don't clean up scanner here as another menu might be opening
    }
    
    override fun onMenuNodesChanged(menuView: MenuView) {
        Log.d(TAG, "Menu nodes changed, updating scanner")
        val nodes = menuView.getSelectableNodes()
        
        if (nodes.isEmpty()) {
            Log.w(TAG, "Menu nodes changed but no selectable nodes available")
            return
        }
        
        headControlScanner?.setNodes(nodes)
        headControlScanner?.initializeSelectionNear(currentX.toFloat(), currentY.toFloat())
    }
    
    override fun onAllMenusClosed() {
        Log.d(TAG, "All menus closed, returning to cursor mode")
        headControlScanner?.clear()
        headControlScanner = null
        showPointerIfAllowed()
        repeatJob?.cancel()
        repeatJob = null
        activeDirection = null
        heldDirection = null
    }
}
