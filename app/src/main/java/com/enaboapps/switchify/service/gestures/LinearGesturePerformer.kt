package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.execution.GestureDispatcher
import com.enaboapps.switchify.service.gestures.execution.GesturePathBuilder
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
 * - HOLD_AND_DRAG: Hold-then-drag with enhanced visual confirmation
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

    companion object {
        private const val TAG = "LinearGesturePerformer"
    }

    /**
     * The current point visual
     */
    private val gestureVisualManager = GestureVisualManager(accessibilityService)

    // State management is now handled by GestureStateManager

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

        if (showMessage) {
            showGestureMessage(type)
        }
        gestureVisualManager.showStaticCircle(
            startPoint.x.toInt(),
            startPoint.y.toInt()
        )
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
            gestureVisualManager.hideCircle()
            return
        }

        val calculatedEndPoint = endPoint ?: calculateEndPoint(gestureType, startPoint)
        performGesture(gestureType, startPoint, calculatedEndPoint)
        gestureLockManager.setLockedGestureData(
            GestureData(
                gestureType,
                startPoint,
                calculatedEndPoint
            )
        )

        GestureStateManager.endGesture()
        gestureVisualManager.hideCircle()
    }

    /**
     * Cancels the current gesture.
     */
    fun cancelGesture() {
        GestureStateManager.cancelGesture()
        gestureVisualManager.hideCircle()
    }

    // State reset is now handled by GestureStateManager

    /**
     * Displays a message to the user based on the gesture type.
     *
     * @param type The type of gesture being performed.
     */
    private fun showGestureMessage(type: GestureType) {
        val messageResId = when (type) {
            GestureType.HOLD_AND_DRAG -> R.string.hud_select_hold_and_drag
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
            GestureType.DRAG, GestureType.CUSTOM_SWIPE, GestureType.HOLD_AND_DRAG -> GesturePoint.getPoint()
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
     * Core gesture execution method integrating unified dispatch pipeline with visual feedback.
     *
     * This method represents the heart of the linear gesture execution system, coordinating:
     * 1. Visual feedback through animated arrow display showing gesture path
     * 2. Gesture path creation using factory pattern for consistent behavior
     * 3. Data packaging for pattern recording and gesture lock integration
     * 4. Unified dispatch through GestureDispatcher with comprehensive error handling
     *
     * Path Creation Strategy:
     * - HOLD_AND_DRAG: Uses specialized two-stroke path with hold-then-drag timing
     * - All other linear gestures: Uses standard linear path with type-appropriate duration
     * - Leverages GesturePathBuilder factory pattern for consistent path creation
     *
     * Visual Feedback Integration:
     * - Displays animated arrow showing gesture path for user confirmation
     * - Coordinates timing with actual gesture execution for synchronized feedback
     * - Uses gesture type to determine appropriate animation duration
     *
     * Unified Dispatch Integration:
     * - Creates complete GestureData package for pattern recording and gesture lock
     * - Uses GestureDispatcher for consistent error handling and state management
     * - Integrates with accessibility service dispatch through unified pipeline
     *
     * Error Handling:
     * - Comprehensive exception handling prevents gesture execution failures from affecting system
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
            showVisualFeedback(start, end, type)

            // Create gesture description using unified path builder
            val gestureDescription = when (type) {
                GestureType.HOLD_AND_DRAG -> {
                    GesturePathBuilder.createHoldAndDragPath(start, end)
                }

                else -> {
                    val duration = GesturePathBuilder.getDurationForGestureType(type)
                    GesturePathBuilder.createLinearPath(start, end, duration)
                }
            }

            Log.d(TAG, "About to dispatch gesture: $type")
            // Create gesture data for pattern recording and gesture lock
            val gestureData = GestureData(type, start, end)

            // Dispatch using unified dispatcher with gesture data
            gestureDispatcher.dispatch(gestureDescription, type, gestureData)
            Log.d(TAG, "Gesture dispatched successfully: $type")
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
     * Determines the duration of a gesture based on its type.
     *
     * @param type The type of gesture.
     * @return The duration of the gesture in milliseconds.
     */
    private fun getDurationForGestureType(type: GestureType): Long {
        return when (type) {
            GestureType.DRAG, GestureType.HOLD_AND_DRAG -> GestureData.DRAG_DURATION
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
}
