package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.execution.GestureDispatcher
import com.enaboapps.switchify.service.gestures.execution.GesturePathBuilder
import com.enaboapps.switchify.service.gestures.placement.FingerMode
import com.enaboapps.switchify.service.gestures.placement.FingerModePreferences
import com.enaboapps.switchify.service.gestures.placement.FingerPlacementAlgorithm
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualManager
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.ServiceMessageHUD

/**
 * Two-phase linear gesture execution engine implementing the start/confirm/execute pattern.
 *
 * Architecture & Design:
 * This class implements a sophisticated two-phase gesture system that separates gesture
 * initiation from execution, enabling user confirmation and providing enhanced accessibility
 * features. The design supports both immediate execution and deferred execution based on
 * user interaction patterns.
 *
 * Two-Phase Execution Model:
 * 1. Phase 1 (Start): User initiates gesture, system shows visual feedback and awaits confirmation
 * 2. Phase 2 (End): User confirms gesture, system calculates final parameters and executes
 *
 * This model provides several accessibility benefits:
 * - Users can preview gesture path before execution
 * - Accidental gesture initiation can be cancelled without effect
 * - Visual feedback helps users understand gesture behavior
 * - Flexible end point calculation supports both explicit and computed targets
 *
 * Key Integration Points:
 * - GestureStateManager: Centralized state tracking for gesture lifecycle
 * - GestureDispatcher: Unified execution pipeline with error handling
 * - GesturePathBuilder: Standardized path creation for consistent behavior
 * - GestureVisualManager: Visual feedback coordination throughout gesture lifecycle
 * - GestureLockManager: Gesture storage for repeat functionality
 *
 * Supported Gesture Types:
 * - DRAG: Direct movement with immediate visual feedback
 * - CUSTOM_SWIPE: User-defined direction and distance
 * - SWIPE_UP/DOWN/LEFT/RIGHT: Predefined directional movements
 * - SCROLL_UP/DOWN/LEFT/RIGHT: Content scrolling with appropriate distances
 *
 * Thread Safety:
 * - All state operations are coordinated through thread-safe GestureStateManager
 * - Visual feedback operations use coroutine-based coordination
 * - Android accessibility service calls are main-thread synchronized
 *
 * @property accessibilityService Android accessibility service for gesture dispatch
 * @property gestureLockManager Gesture storage manager for repeat functionality
 */
class LinearGesturePerformer(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val gestureLockManager: GestureLockManager
) {
    private val gestureDispatcher = GestureDispatcher(accessibilityService)
    private val fingerPlacementAlgorithm = FingerPlacementAlgorithm()
    private val preferenceManager = PreferenceManager(accessibilityService)

    companion object {
        private const val TAG = "LinearGesturePerformer"
    }

    /**
     * The current point visual
     */
    private val gestureVisualManager = GestureVisualManager(accessibilityService)

    // State management is now handled by GestureStateManager

    /**
     * Phase 1: Initiates a two-phase linear gesture with explicit finger count override.
     *
     * This overloaded version bypasses user preference and uses the specified finger count,
     * primarily used for pattern playback where gestures must use their recorded finger count.
     *
     * @param type Gesture type determining calculation and execution behavior
     * @param explicitFingerCount Override finger count, bypassing user preference
     * @param showMessage Whether to display instructional HUD messages to user
     * @param startingPoint Explicit start coordinates, defaults to current point position
     */
    fun startGesture(
        type: GestureType,
        explicitFingerCount: Int,
        showMessage: Boolean = true,
        startingPoint: PointF? = null
    ) {
        val startPoint = startingPoint ?: GesturePoint.getPoint()

        Log.d(
            TAG,
            "startGesture called with explicit finger count - type: $type, fingerCount: $explicitFingerCount, startPoint: $startPoint"
        )

        // Use unified state manager
        if (!GestureStateManager.startGesture(type, startPoint)) {
            Log.w(TAG, "startGesture failed - GestureStateManager.startGesture returned false")
            Log.d(TAG, "GestureStateManager state: ${GestureStateManager.getStateSummary()}")
            return // Already performing gesture or too soon since last gesture
        }

        Log.d(TAG, "startGesture successful - state set in GestureStateManager")
        Log.d(
            TAG,
            "GestureStateManager state after start: ${GestureStateManager.getStateSummary()}"
        )

        // Calculate finger placement using explicit finger count (pattern playback mode)
        var fingerPlacement: com.enaboapps.switchify.service.gestures.placement.FingerPlacement? =
            null
        try {
            // Convert finger count to appropriate FingerMode for algorithm
            val overrideFingerMode = when (explicitFingerCount.coerceIn(1, 5)) {
                1 -> com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE
                2 -> com.enaboapps.switchify.service.gestures.placement.FingerMode.TWO
                3 -> com.enaboapps.switchify.service.gestures.placement.FingerMode.THREE
                4 -> com.enaboapps.switchify.service.gestures.placement.FingerMode.FOUR
                5 -> com.enaboapps.switchify.service.gestures.placement.FingerMode.FIVE
                else -> com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE
            }

            val screenBounds = getScreenBounds()

            fingerPlacement = fingerPlacementAlgorithm.calculateFingerPlacement(
                gestureType = type,
                targetPoint = startPoint,
                userFingerMode = overrideFingerMode, // Use explicit finger mode override
                screenBounds = screenBounds
            )

            // Store finger placement in state manager for use in endGesture()
            GestureStateManager.setCurrentFingerPlacement(fingerPlacement)

            Log.d(
                TAG,
                "Explicit finger placement calculated and stored: ${fingerPlacement.getDescription()}"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to calculate explicit finger placement, falling back to single-finger",
                e
            )
            Log.e(TAG, "Exception details: ${e.message}")
            e.printStackTrace()
            // Continue with single-finger gesture - finger placement will be null
        }

        if (showMessage) {
            showGestureMessage(type)
        }

        // Show appropriate visual feedback based on finger placement
        if (fingerPlacement != null && fingerPlacement.fingerCount > 1) {
            // Show multi-finger visual feedback with finger positions
            gestureVisualManager.showMultiFingerVisual(fingerPlacement)
            Log.d(
                TAG,
                "Showing multi-finger start visual for ${fingerPlacement.fingerCount} fingers (explicit count)"
            )
        } else {
            // Fall back to single finger visual feedback
            gestureVisualManager.showStaticCircle(
                startPoint.x.toInt(),
                startPoint.y.toInt()
            )
        }
    }

    /**
     * Phase 1: Initiates a two-phase linear gesture with immediate visual feedback.
     *
     * This method begins the two-phase gesture execution model by:
     * 1. Validating gesture start conditions through GestureStateManager
     * 2. Setting up visual feedback to show the gesture initiation point
     * 3. Displaying instructional messages to guide user interaction
     * 4. Preparing the system for gesture completion via endGesture()
     *
     * State Management:
     * - Uses GestureStateManager.startGesture() for thread-safe state initialization
     * - Respects gesture timing constraints to prevent rapid-fire gestures
     * - Handles concurrent gesture prevention through atomic state operations
     * - Maintains gesture context (type, start point) for later completion
     *
     * Visual Feedback:
     * - Shows static circle at start point for immediate user confirmation
     * - Displays contextual HUD messages for gesture types requiring instruction
     * - Coordinates with GestureVisualManager for consistent feedback patterns
     *
     * Error Handling:
     * - Gracefully handles state manager failures (concurrent gestures, timing)
     * - Comprehensive logging for debugging gesture initiation issues
     * - Fails safely without affecting system state when preconditions aren't met
     *
     * @param type Gesture type determining calculation and execution behavior
     * @param showMessage Whether to display instructional HUD messages to user
     * @param startingPoint Explicit start coordinates, defaults to current point position
     */
    fun startGesture(
        type: GestureType,
        showMessage: Boolean = true,
        startingPoint: PointF? = null
    ) {
        val startPoint = startingPoint ?: GesturePoint.getPoint()

        Log.d(TAG, "startGesture called - type: $type, startPoint: $startPoint")

        // Use unified state manager
        if (!GestureStateManager.startGesture(type, startPoint)) {
            Log.w(TAG, "startGesture failed - GestureStateManager.startGesture returned false")
            Log.d(TAG, "GestureStateManager state: ${GestureStateManager.getStateSummary()}")
            return // Already performing gesture or too soon since last gesture
        }

        Log.d(TAG, "startGesture successful - state set in GestureStateManager")
        Log.d(
            TAG,
            "GestureStateManager state after start: ${GestureStateManager.getStateSummary()}"
        )

        // Calculate finger placement for multi-finger gesture support
        var fingerPlacement: com.enaboapps.switchify.service.gestures.placement.FingerPlacement? =
            null
        try {
            val fingerMode = getCurrentFingerMode()
            val screenBounds = getScreenBounds()

            fingerPlacement = fingerPlacementAlgorithm.calculateFingerPlacement(
                gestureType = type,
                targetPoint = startPoint,
                userFingerMode = fingerMode,
                screenBounds = screenBounds
            )

            // Store finger placement in state manager for use in endGesture()
            GestureStateManager.setCurrentFingerPlacement(fingerPlacement)

            Log.d(
                TAG,
                "Finger placement calculated and stored: ${fingerPlacement.getDescription()}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate finger placement, falling back to single-finger", e)
            Log.e(TAG, "Exception details: ${e.message}")
            e.printStackTrace()
            // Continue with single-finger gesture - finger placement will be null
        }

        if (showMessage) {
            showGestureMessage(type)
        }

        // Show appropriate visual feedback based on finger placement
        if (fingerPlacement != null && fingerPlacement.fingerCount > 1) {
            // Show multi-finger visual feedback with finger positions
            gestureVisualManager.showMultiFingerVisual(fingerPlacement)
            Log.d(
                TAG,
                "Showing multi-finger start visual for ${fingerPlacement.fingerCount} fingers"
            )
        } else {
            // Fall back to single finger visual feedback
            gestureVisualManager.showStaticCircle(
                startPoint.x.toInt(),
                startPoint.y.toInt()
            )
        }
    }

    /**
     * Phase 2: Completes the two-phase gesture execution with full system integration.
     *
     * This method represents the culmination of the two-phase gesture model, where:
     * 1. User confirmation triggers the transition from preview to execution
     * 2. System retrieves stored gesture context from GestureStateManager
     * 3. End point calculation uses either explicit coordinates or smart computation
     * 4. Full gesture execution occurs through the unified dispatch pipeline
     * 5. Pattern recording and gesture lock integration for user learning and repeat functionality
     *
     * Execution Flow:
     * 1. State Validation: Retrieves and validates gesture context from state manager
     * 2. End Point Resolution: Uses explicit end point or calculates based on gesture type
     * 3. Gesture Execution: Delegates to performGesture() for unified dispatch
     * 4. Pattern Storage: Records gesture in GestureLockManager for repeat functionality
     * 5. State Cleanup: Cleans up visual feedback and resets gesture state
     *
     * Smart End Point Calculation:
     * - DRAG/CUSTOM_SWIPE: Uses current point position for dynamic targeting
     * - SWIPE directions: Calculates screen-relative distances for consistent behavior
     * - SCROLL directions: Applies content-appropriate distances for scrolling actions
     *
     * Integration Points:
     * - GestureStateManager: State retrieval and cleanup coordination
     * - performGesture(): Unified execution pipeline with visual feedback
     * - GestureLockManager: Pattern storage for gesture repeat functionality
     * - GestureVisualManager: Visual feedback cleanup and coordination
     *
     * Error Recovery:
     * - Handles invalid state gracefully with automatic cleanup
     * - Comprehensive logging for debugging gesture completion issues
     * - Ensures system returns to ready state even on failures
     *
     * @param endPoint Optional explicit end coordinates, defaults to calculated end point
     */
    fun endGesture(endPoint: PointF? = null) {
        val startPoint = GestureStateManager.getCurrentGestureStartPoint()
        val gestureType = GestureStateManager.getCurrentGestureType()

        // Debug logging to identify the issue
        Log.d(TAG, "endGesture called - startPoint: $startPoint, gestureType: $gestureType")
        Log.d(TAG, "GestureStateManager state: ${GestureStateManager.getStateSummary()}")

        if (startPoint == null || gestureType == null) {
            Log.w(TAG, "endGesture failed - startPoint or gestureType is null")
            GestureStateManager.cancelGesture()
            gestureVisualManager.hideAllVisuals()
            return
        }

        val calculatedEndPoint = endPoint ?: calculateEndPoint(gestureType, startPoint)
        performGesture(gestureType, startPoint, calculatedEndPoint)

        // Get finger count from current finger placement for gesture lock
        val fingerPlacement = GestureStateManager.getCurrentFingerPlacement()
        val fingerCount = fingerPlacement?.fingerCount ?: 1
        val fingerMode =
            if (fingerCount > 1) getCurrentFingerMode() else com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE

        gestureLockManager.setLockedGestureData(
            GestureData(
                gestureType = gestureType,
                startPoint = startPoint,
                endPoint = calculatedEndPoint,
                fingerCount = fingerCount,
                fingerMode = fingerMode
            )
        )

        GestureStateManager.endGesture()
        gestureVisualManager.hideAllVisuals()
    }

    /**
     * Cancels the current gesture.
     */
    fun cancelGesture() {
        GestureStateManager.cancelGesture()
        gestureVisualManager.hideAllVisuals()
    }

    // State reset is now handled by GestureStateManager

    /**
     * Displays a message to the user based on the gesture type.
     *
     * @param type The type of gesture being performed.
     */
    private fun showGestureMessage(type: GestureType) {
        val messageResId = when (type) {
            GestureType.DRAG -> R.string.hud_select_drag
            GestureType.CUSTOM_SWIPE -> R.string.hud_select_swipe
            else -> return
        }
        ServiceMessageHUD.instance.showMessage(
            messageResId,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )
    }

    /**
     * Calculates the end point of a gesture based on its type and starting point.
     *
     * @param type The type of gesture.
     * @param start The starting point of the gesture.
     * @return The calculated end point of the gesture.
     */
    private fun calculateEndPoint(type: GestureType, start: PointF): PointF {
        val screenWidth = ScreenUtils.getWidth(accessibilityService)
        val screenHeight = ScreenUtils.getHeight(accessibilityService)
        return when (type) {
            GestureType.DRAG, GestureType.CUSTOM_SWIPE -> GesturePoint.getPoint()
            GestureType.SWIPE_UP, GestureType.SCROLL_DOWN -> PointF(
                start.x,
                start.y - screenHeight / 5f
            )

            GestureType.SWIPE_DOWN, GestureType.SCROLL_UP -> PointF(
                start.x,
                start.y + screenHeight / 5f
            )

            GestureType.SWIPE_LEFT, GestureType.SCROLL_RIGHT -> PointF(
                start.x - screenWidth / 4f,
                start.y
            )

            GestureType.SWIPE_RIGHT, GestureType.SCROLL_LEFT -> PointF(
                start.x + screenWidth / 4f,
                start.y
            )

            else -> start
        }
    }

    /**
     * Core gesture execution method integrating unified dispatch pipeline with multi-finger support.
     *
     * This method represents the heart of the linear gesture execution system, coordinating:
     * 1. Visual feedback through animated arrow display showing gesture path
     * 2. Multi-finger or single-finger gesture path creation based on user preferences
     * 3. Data packaging for pattern recording and gesture lock integration
     * 4. Unified dispatch through GestureDispatcher with comprehensive error handling
     *
     * Path Creation Strategy:
     * - Multi-finger mode: Uses createDynamicLinearPath for coordinated multi-finger gestures
     * - Single-finger mode: Uses traditional single-finger paths for compatibility
     * - Leverages GesturePathBuilder factory pattern for consistent behavior
     *
     * Visual Feedback Integration:
     * - Multi-finger: Shows visual feedback for all finger paths
     * - Single-finger: Shows traditional single-path feedback
     * - Coordinates timing with actual gesture execution for synchronized feedback
     *
     * Unified Dispatch Integration:
     * - Creates complete GestureData package for pattern recording and gesture lock
     * - Uses GestureDispatcher for consistent error handling and state management
     * - Integrates with accessibility service dispatch through unified pipeline
     *
     * Error Handling:
     * - Comprehensive exception handling prevents gesture execution failures from affecting system
     * - Falls back to single-finger mode if multi-finger calculation fails
     * - Detailed logging for debugging gesture execution issues
     * - Graceful degradation maintains system stability on execution errors
     *
     * @param type Gesture type determining path creation and timing behavior
     * @param start Gesture starting coordinates for path calculation
     * @param end Gesture ending coordinates for path calculation
     */
    private fun performGesture(type: GestureType, start: PointF, end: PointF) {
        try {
            Log.d(TAG, "performGesture called - type: $type, start: $start, end: $end")

            // Check if we have multi-finger placement to work with
            val fingerPlacement = GestureStateManager.getCurrentFingerPlacement()

            if (fingerPlacement != null) {
                // Multi-finger gesture execution path
                Log.d(
                    TAG,
                    "Executing multi-finger gesture with placement: ${fingerPlacement.getDescription()}"
                )

                // Calculate end positions for all fingers
                val endFingerPositions = calculateEndFingerPositions(fingerPlacement, start, end)

                // Get start finger positions
                val startFingerPositions = when (fingerPlacement) {
                    is com.enaboapps.switchify.service.gestures.placement.SingleFingerPlacement -> {
                        listOf(fingerPlacement.primaryPoint)
                    }

                    is com.enaboapps.switchify.service.gestures.placement.TwoFingerPlacement -> {
                        listOf(fingerPlacement.primaryPoint, fingerPlacement.secondaryPoint)
                    }

                    is com.enaboapps.switchify.service.gestures.placement.MultiFingerPlacement -> {
                        fingerPlacement.fingerPoints
                    }
                }

                // Show visual feedback for all finger paths
                showMultiFingerVisualFeedback(startFingerPositions, endFingerPositions, type)

                // Create multi-finger gesture description
                val gestureDescription = GesturePathBuilder.createDynamicLinearPath(
                    type,
                    startFingerPositions,
                    endFingerPositions
                )

                Log.d(
                    TAG,
                    "About to dispatch multi-finger gesture: $type with ${startFingerPositions.size} fingers"
                )
                // Create gesture data for pattern recording and gesture lock with finger count
                val fingerCount = startFingerPositions.size
                val currentFingerMode = getCurrentFingerMode()
                val gestureData = GestureData(
                    gestureType = type,
                    startPoint = start,
                    endPoint = end,
                    fingerCount = fingerCount,
                    fingerMode = currentFingerMode
                )

                Log.d(TAG, "Recording gesture with $fingerCount fingers (mode: $currentFingerMode)")

                // Dispatch using unified dispatcher with gesture data
                gestureDispatcher.dispatch(gestureDescription, type, gestureData)
                Log.d(TAG, "Multi-finger gesture dispatched successfully: $type")

            } else {
                // Fall back to single-finger gesture execution (legacy behavior)
                Log.d(TAG, "Executing single-finger gesture (legacy mode)")
                showVisualFeedback(start, end, type)

                // Create gesture description using unified path builder
                val duration = GesturePathBuilder.getDurationForGestureType(type)
                val gestureDescription = GesturePathBuilder.createLinearPath(start, end, duration)

                Log.d(TAG, "About to dispatch single-finger gesture: $type")
                // Create gesture data for pattern recording and gesture lock with finger count
                val gestureData = GestureData(
                    gestureType = type,
                    startPoint = start,
                    endPoint = end,
                    fingerCount = 1,
                    fingerMode = com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE
                )

                Log.d(TAG, "Recording gesture with 1 finger (single-finger mode)")

                // Dispatch using unified dispatcher with gesture data
                gestureDispatcher.dispatch(gestureDescription, type, gestureData)
                Log.d(TAG, "Single-finger gesture dispatched successfully: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing gesture", e)
        }
    }

    /**
     * Provides visual feedback of the gesture path.
     *
     * @param start The starting point of the gesture.
     * @param end The ending point of the gesture.
     * @param type The type of gesture.
     */
    private fun showVisualFeedback(start: PointF, end: PointF, type: GestureType) {
        val duration = getDurationForGestureType(type)
        gestureVisualManager.showArrowAnimation(
            start.x.toInt(), start.y.toInt(),
            end.x.toInt(), end.y.toInt(),
            duration
        )
    }

    /**
     * Provides multi-finger visual feedback showing paths for all fingers.
     * Shows coordinated visual feedback for all finger paths in multi-finger gestures,
     * giving users a clear understanding of the gesture motion with synchronized arrows.
     *
     * @param startPositions List of start positions for all fingers
     * @param endPositions List of end positions for all fingers
     * @param type The type of gesture determining timing
     */
    private fun showMultiFingerVisualFeedback(
        startPositions: List<PointF>,
        endPositions: List<PointF>,
        type: GestureType
    ) {
        val duration = getDurationForGestureType(type)

        // Use coordinated multi-finger arrow animation for better visual feedback
        gestureVisualManager.showMultiFingerArrowAnimation(
            startPositions,
            endPositions,
            duration
        )

        Log.d(
            TAG,
            "Showing coordinated multi-finger visual feedback for ${startPositions.size} finger paths"
        )
    }

    /**
     * Determines the duration of a gesture based on its type.
     *
     * @param type The type of gesture.
     * @return The duration of the gesture in milliseconds.
     */
    private fun getDurationForGestureType(type: GestureType): Long {
        return when (type) {
            GestureType.DRAG -> GestureData.DRAG_DURATION
            GestureType.SCROLL_UP, GestureType.SCROLL_DOWN, GestureType.SCROLL_LEFT, GestureType.SCROLL_RIGHT -> GestureData.SCROLL_DURATION
            else -> GestureData.SWIPE_DURATION
        }
    }

    /**
     * Checks if a gesture is currently being performed.
     *
     * @return True if a gesture is in progress, false otherwise.
     */
    fun isPerformingGesture(): Boolean = GestureStateManager.isGestureInProgress()

    /**
     * Cancels any ongoing gestures and resets the gesture state.
     */
    fun cancelOngoingGestures() {
        GestureStateManager.cancelGesture()
    }

    // === Helper Methods for Multi-Finger Support ===

    /**
     * Gets the current finger mode preference from user settings.
     *
     * @return Current FingerMode setting, defaults to ONE if not set
     */
    private fun getCurrentFingerMode(): FingerMode {
        return FingerModePreferences.getCurrentFingerMode(preferenceManager)
    }

    /**
     * Gets the current screen bounds for finger placement calculations.
     *
     * @return Screen bounds rectangle, or default bounds if service unavailable
     */
    private fun getScreenBounds(): Rect {
        return accessibilityService?.let { service ->
            val displayMetrics = service.resources.displayMetrics
            Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
        } ?: Rect(0, 0, 1080, 1920) // Default bounds as fallback
    }

    /**
     * Calculates end finger positions by translating all start finger positions by the gesture vector.
     * This maintains the relative finger positioning while moving all fingers in the same direction and distance.
     *
     * @param startFingerPlacement The original finger placement from gesture start
     * @param gestureStartPoint The single start point of the gesture
     * @param gestureEndPoint The single end point of the gesture
     * @return List of end positions for all fingers, maintaining relative spacing
     */
    private fun calculateEndFingerPositions(
        startFingerPlacement: com.enaboapps.switchify.service.gestures.placement.FingerPlacement,
        gestureStartPoint: PointF,
        gestureEndPoint: PointF
    ): List<PointF> {
        // Calculate the gesture vector (direction and distance)
        val gestureVectorX = gestureEndPoint.x - gestureStartPoint.x
        val gestureVectorY = gestureEndPoint.y - gestureStartPoint.y

        Log.d(
            TAG,
            "Calculating end finger positions - gesture vector: ($gestureVectorX, $gestureVectorY)"
        )

        // Get all finger positions from the placement
        val startFingerPositions = when (startFingerPlacement) {
            is com.enaboapps.switchify.service.gestures.placement.SingleFingerPlacement -> {
                listOf(startFingerPlacement.primaryPoint)
            }

            is com.enaboapps.switchify.service.gestures.placement.TwoFingerPlacement -> {
                listOf(startFingerPlacement.primaryPoint, startFingerPlacement.secondaryPoint)
            }

            is com.enaboapps.switchify.service.gestures.placement.MultiFingerPlacement -> {
                startFingerPlacement.fingerPoints
            }
        }

        // Translate all finger positions by the gesture vector
        val endFingerPositions = startFingerPositions.map { startPos ->
            val endPos = PointF(startPos.x + gestureVectorX, startPos.y + gestureVectorY)
            Log.d(
                TAG,
                "Finger position: (${startPos.x}, ${startPos.y}) -> (${endPos.x}, ${endPos.y})"
            )
            endPos
        }

        // Apply screen bounds clamping to keep fingers within screen
        val screenBounds = getScreenBounds()
        val clampedEndPositions = endFingerPositions.map { pos ->
            val clampedPos = PointF(
                pos.x.coerceIn(
                    screenBounds.left.toFloat() + 32f,
                    screenBounds.right.toFloat() - 32f
                ),
                pos.y.coerceIn(
                    screenBounds.top.toFloat() + 32f,
                    screenBounds.bottom.toFloat() - 32f
                )
            )
            clampedPos
        }

        Log.d(TAG, "Calculated ${clampedEndPositions.size} end finger positions")
        return clampedEndPositions
    }
}
