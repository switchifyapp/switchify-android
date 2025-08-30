package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import android.util.Log
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.execution.GestureDispatcher
import com.enaboapps.switchify.service.gestures.execution.GesturePathBuilder
import com.enaboapps.switchify.service.gestures.execution.GestureTimingCoordinator
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualManager
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer

/**
 * Central facade for the entire gesture system, orchestrating all gesture operations.
 *
 * Architecture:
 * - Acts as the primary entry point for all gesture requests from the UI layer
 * - Delegates to specialized components for different aspects of gesture handling
 * - Integrates unified execution pipeline with visual feedback and state management
 *
 * Key Components:
 * - GestureDispatcher: Unified gesture dispatch with error handling and callbacks
 * - LinearGesturePerformer: Two-phase linear gesture handling (start/end)
 * - GestureVisualManager: Visual feedback coordination for gesture actions
 * - GesturePathBuilder: Standardized gesture path creation using factory pattern
 * - GestureTimingCoordinator: Timing management for complex multi-stage gestures
 * - GestureStateManager: Centralized state tracking across all gesture operations
 *
 * Design Patterns:
 * - Singleton: Single instance manages all gesture operations
 * - Facade: Simplifies complex gesture subsystem interactions
 * - Strategy: Different gesture types handled by specialized performers
 * - Factory: GesturePathBuilder creates appropriate gesture descriptions
 *
 * Thread Safety:
 * - All gesture operations are coordinated through thread-safe GestureStateManager
 * - Visual feedback and timing operations use coroutines with proper synchronization
 * - Android accessibility service callbacks are handled on main thread
 */
class GestureManager private constructor() {
    companion object {
        val instance: GestureManager by lazy { GestureManager() }
    }

    private var accessibilityService: SwitchifyAccessibilityService? = null
    private var preferenceManager: PreferenceManager? = null
    private lateinit var linearGesturePerformer: LinearGesturePerformer
    private lateinit var gestureVisualManager: GestureVisualManager
    private lateinit var gestureDispatcher: GestureDispatcher
    private lateinit var timingCoordinator: GestureTimingCoordinator

    /**
     * Initializes the complete gesture system architecture with all required components.
     *
     * This method establishes the unified execution pipeline that consolidates:
     * - Gesture dispatch operations through GestureDispatcher
     * - Visual feedback coordination via GestureVisualManager
     * - Linear gesture handling through LinearGesturePerformer
     * - Timing management for complex gestures via GestureTimingCoordinator
     * - Pattern recording and gesture lock through GestureLockManager
     * - Auto-scroll functionality for repetitive gestures
     *
     * Initialization Order:
     * 1. Core service dependencies (accessibility service, preferences)
     * 2. Unified execution pipeline components (dispatcher, coordinator)
     * 3. Specialized gesture performers (linear, visual, auto-scroll)
     * 4. Pattern recording and gesture lock systems
     *
     * @param accessibilityService The SwitchifyAccessibilityService providing Android accessibility APIs
     */
    fun setup(accessibilityService: SwitchifyAccessibilityService) {
        this.accessibilityService = accessibilityService
        AutoScrollManager.getInstance().init(accessibilityService)
        preferenceManager = PreferenceManager(accessibilityService)

        // Initialize unified execution pipeline components
        gestureDispatcher = GestureDispatcher(accessibilityService)
        timingCoordinator = GestureTimingCoordinator()

        linearGesturePerformer =
            LinearGesturePerformer(accessibilityService, GestureLockManager.instance)
        gestureVisualManager = GestureVisualManager(accessibilityService)
        GestureLockManager.instance.init(accessibilityService)
    }

    /**
     * Intelligent point selection that optionally snaps to nearby accessible nodes.
     *
     * This method implements assisted selection, a key accessibility feature that:
     * - Analyzes the current gesture point from GesturePoint.getPoint()
     * - When enabled, uses NodeExaminer to find the closest accessible UI element
     * - Snaps the gesture target to the center of the closest node for better accuracy
     * - Falls back to raw point coordinates when assisted selection is disabled
     *
     * Benefits:
     * - Improves gesture accuracy for users with motor impairments
     * - Reduces need for precise targeting on small UI elements
     * - Maintains compatibility with existing gesture workflows
     *
     * @return PointF representing the optimized gesture target coordinates
     */
    private fun getAssistedCurrentPoint(): PointF {
        return if (preferenceManager?.getBooleanValue(PreferenceManager.PREFERENCE_KEY_ASSISTED_SELECTION) == true) {
            NodeExaminer.getClosestNodeToPoint(GesturePoint.getPoint())
        } else {
            GesturePoint.getPoint()
        }
    }

    /**
     * Executes a single tap gesture with full pipeline integration.
     *
     * Execution Flow:
     * 1. Point Resolution: Uses explicit coordinates or assisted selection
     * 2. Visual Feedback: Shows static circle at tap location for user confirmation
     * 3. Gesture Creation: GesturePathBuilder creates standardized tap path
     * 4. Data Packaging: Creates GestureData for pattern recording and gesture lock
     * 5. Unified Dispatch: GestureDispatcher handles execution with error callbacks
     *
     * Integration Points:
     * - GestureVisualManager: Provides immediate visual feedback
     * - GesturePathBuilder: Creates consistent tap gesture description
     * - GestureDispatcher: Unified error handling and result callbacks
     * - GesturePatternRecorder: Records gesture for pattern learning (via dispatcher)
     * - GestureLockManager: Stores gesture data for potential locking (via dispatcher)
     *
     * @param x Optional explicit x coordinate, null uses assisted selection
     * @param y Optional explicit y coordinate, null uses assisted selection
     */
    fun performTap(x: Int? = null, y: Int? = null) {
        try {
            accessibilityService?.let {
                val point = if (x != null && y != null) {
                    PointF(x.toFloat(), y.toFloat())
                } else {
                    getAssistedCurrentPoint()
                }

                // Show visual feedback
                val duration = GestureData.TAP_DURATION
                gestureVisualManager.showStaticCircle(
                    point.x.toInt(),
                    point.y.toInt(),
                    duration
                )

                // Create and dispatch gesture using unified pipeline
                val gestureDescription = GesturePathBuilder.createTapPath(point, duration)
                val gestureData = GestureData(GestureType.TAP, point)
                gestureDispatcher.dispatch(gestureDescription, GestureType.TAP, gestureData)
            }
        } catch (e: Exception) {
            Log.e("GestureManager", "Error performing tap", e)
        }
    }

    /**
     * Performs a double tap gesture at the current point.
     *
     * @param x The x coordinate of the tap gesture. If null, the current point will be used.
     * @param y The y coordinate of the tap gesture. If null, the current point will be used.
     */
    fun performDoubleTap(x: Int? = null, y: Int? = null) {
        try {
            accessibilityService?.let {
                val point = if (x != null && y != null) {
                    PointF(x.toFloat(), y.toFloat())
                } else {
                    getAssistedCurrentPoint()
                }

                // Coordinate timing and visual feedback
                val handler = timingCoordinator.createDefaultHandler(
                    onReady = { _, _ ->
                        // Show first tap visual
                        gestureVisualManager.showStaticCircle(
                            point.x.toInt(),
                            point.y.toInt(),
                            GestureData.TAP_DURATION
                        )
                    }
                )

                timingCoordinator.coordinateDoubleTap(
                    handler,
                    gestureVisualManager,
                    GestureData.DOUBLE_TAP_INTERVAL,
                    GestureData.TAP_DURATION
                )

                // Create and dispatch gesture using unified pipeline
                val gestureDescription = GesturePathBuilder.createDoubleTapPath(point)
                val gestureData = GestureData(GestureType.DOUBLE_TAP, point)
                gestureDispatcher.dispatch(gestureDescription, GestureType.DOUBLE_TAP, gestureData)
            }
        } catch (e: Exception) {
            Log.e("GestureManager", "Error performing double tap", e)
        }
    }

    /**
     * Performs a tap and hold gesture at the current point.
     *
     * @param x The x coordinate of the tap gesture. If null, the current point will be used.
     * @param y The y coordinate of the tap gesture. If null, the current point will be used.
     */
    fun performTapAndHold(x: Int? = null, y: Int? = null) {
        try {
            accessibilityService?.let {
                val point = if (x != null && y != null) {
                    PointF(x.toFloat(), y.toFloat())
                } else {
                    getAssistedCurrentPoint()
                }

                // Show visual feedback
                val duration = GestureData.TAP_AND_HOLD_DURATION
                gestureVisualManager.showStaticCircle(
                    point.x.toInt(),
                    point.y.toInt(),
                    duration
                )

                // Create and dispatch gesture using unified pipeline
                val gestureDescription = GesturePathBuilder.createTapAndHoldPath(point, duration)
                val gestureData = GestureData(GestureType.TAP_AND_HOLD, point)
                gestureDispatcher.dispatch(
                    gestureDescription,
                    GestureType.TAP_AND_HOLD,
                    gestureData
                )
            }
        } catch (e: Exception) {
            Log.e("GestureManager", "Error performing tap and hold", e)
        }
    }

    /**
     * Performs the gesture lock action if the gesture lock is enabled.
     *
     * @return True if a locked gesture action was performed, false otherwise.
     */
    fun performGestureLockAction(): Boolean {
        if (isGestureLockEnabled()) {
            GestureLockManager.instance.getLockedGestureData()?.let { gestureData ->
                gestureData.executeGesture()
                return true
            }
        }
        return false
    }

    /**
     * Toggles the gesture lock on or off.
     */
    fun toggleGestureLock() {
        GestureLockManager.instance.toggleGestureLock()
    }

    /**
     * Checks if the gesture lock is currently enabled.
     *
     * @return True if the gesture lock is enabled, false otherwise.
     */
    fun isGestureLockEnabled(): Boolean {
        return GestureLockManager.instance.isGestureLockEngaged() == true
    }

    /**
     * Executes arbitrary custom gestures using the two-phase linear gesture system.
     *
     * This method provides a direct interface to the LinearGesturePerformer for:
     * - Custom gesture implementations not covered by standard gesture methods
     * - Programmatic gesture execution from stored gesture data
     * - Pattern replay functionality from GesturePatternRecorder
     * - Gesture lock execution from GestureLockManager
     *
     * Flow:
     * 1. Initiates gesture using LinearGesturePerformer.startGesture()
     * 2. Immediately completes with LinearGesturePerformer.endGesture()
     * 3. Linear performer handles state management, visual feedback, and dispatch
     *
     * @param gestureData Complete gesture specification including type, start/end points
     * @return True indicating gesture was submitted (not necessarily completed)
     */
    fun performCustomGestureAction(gestureData: GestureData): Boolean {
        linearGesturePerformer.startGesture(gestureData.gestureType, false, gestureData.startPoint)
        linearGesturePerformer.endGesture(gestureData.endPoint)
        return true
    }

    /**
     * Performs a swipe or scroll action.
     *
     * @param type The GestureType of the swipe.
     * @param startPoint The starting point of the gesture.
     */
    fun performSwipeOrScroll(type: GestureType, startPoint: PointF? = null) {
        val point = startPoint ?: GesturePoint.getPoint()
        if (AutoScrollManager.getInstance()
                .startAutoScroll(GestureData(type, point))
        ) return
        linearGesturePerformer.startGesture(type, startingPoint = point)
        linearGesturePerformer.endGesture()
    }

    /**
     * Initiates a drag gesture using the two-phase linear gesture system.
     *
     * Drag gestures allow users to move UI elements by:
     * 1. Starting the gesture at the current position
     * 2. Waiting for user confirmation or automatic completion
     * 3. Executing the drag to the final position via endLinearGesture()
     *
     * State Management: Updates GestureStateManager to track active gesture
     */
    fun startDragGesture() {
        linearGesturePerformer.startGesture(GestureType.DRAG)
    }

    /**
     * Initiates a hold-and-drag gesture with initial hold phase.
     *
     * Hold-and-drag gestures provide better user feedback by:
     * 1. Holding at the start position to indicate selection
     * 2. Then dragging to the end position
     * 3. Useful for drag-and-drop operations requiring visual confirmation
     *
     * State Management: Updates GestureStateManager to track active gesture
     */
    fun startHoldAndDragGesture() {
        linearGesturePerformer.startGesture(GestureType.HOLD_AND_DRAG)
    }

    /**
     * Initiates a custom swipe gesture for user-defined directions and distances.
     *
     * Custom swipes enable flexible gesture input where:
     * - Start point is determined by current point position
     * - End point is determined by user input or automatic calculation
     * - Direction and distance are not constrained to predefined patterns
     *
     * State Management: Updates GestureStateManager to track active gesture
     */
    fun startCustomSwipe() {
        linearGesturePerformer.startGesture(GestureType.CUSTOM_SWIPE)
    }

    /**
     * Completes any active linear gesture using the two-phase system.
     *
     * This method:
     * 1. Retrieves current gesture state from GestureStateManager
     * 2. Determines end point (provided or current point position)
     * 3. Executes the complete gesture through LinearGesturePerformer
     * 4. Integrates with unified dispatch pipeline for consistent handling
     *
     * Critical for: drag, hold-and-drag, custom swipe completion
     */
    fun endLinearGesture() {
        Log.d("GestureManager", "endLinearGesture called")
        linearGesturePerformer.endGesture()
    }

    /**
     * Cancels active linear gesture and resets all related state.
     *
     * Cancellation Process:
     * 1. Stops any in-progress visual feedback
     * 2. Clears gesture state in GestureStateManager
     * 3. Prevents gesture from being executed or recorded
     * 4. Returns system to ready state for next gesture
     *
     * Used when: user changes mind, system error, or conflicting input
     */
    fun cancelLinearGesture() {
        linearGesturePerformer.cancelGesture()
    }

    /**
     * Checks if a linear gesture is currently being performed.
     *
     * @return True if a linear gesture is being performed, false otherwise.
     */
    fun isPerformingLinearGesture(): Boolean {
        return GestureStateManager.isGestureInProgress()
    }

    /**
     * Performs a zoom action.
     *
     * @param type The type of zoom action to perform.
     * @param startPoint The starting point of the gesture.
     */
    fun performZoom(type: GestureType, startPoint: PointF? = null) {
        val point = startPoint ?: GesturePoint.getPoint()
        GestureLockManager.instance.setLockedGestureData(
            GestureData(
                type,
                point
            )
        )
        accessibilityService?.let {
            ZoomGesturePerformer.performZoomAction(type, it, point)
        }
    }
}
