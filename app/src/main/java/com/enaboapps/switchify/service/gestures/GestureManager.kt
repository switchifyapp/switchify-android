package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.GestureStateManager
import com.enaboapps.switchify.service.gestures.execution.GestureDispatcher
import com.enaboapps.switchify.service.gestures.execution.GesturePathBuilder
import com.enaboapps.switchify.service.gestures.execution.GestureTimingCoordinator
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualManager
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer

/**
 * The GestureManager class is responsible for managing and performing various gesture actions
 * in the Switchify accessibility service. It handles taps, swipes, drags, and custom gestures.
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
     * Sets up the GestureManager with the necessary components.
     *
     * @param accessibilityService The SwitchifyAccessibilityService instance.
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
     * Gets the current gesture point, applying assisted selection if enabled.
     *
     * @return The PointF representing the current gesture point.
     */
    private fun getAssistedCurrentPoint(): PointF {
        return if (preferenceManager?.getBooleanValue(PreferenceManager.PREFERENCE_KEY_ASSISTED_SELECTION) == true) {
            NodeExaminer.getClosestNodeToPoint(GesturePoint.getPoint())
        } else {
            GesturePoint.getPoint()
        }
    }

    /**
     * Performs a tap gesture at the current point.
     *
     * @param x The x coordinate of the tap gesture. If null, the current point will be used.
     * @param y The y coordinate of the tap gesture. If null, the current point will be used.
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
                gestureDispatcher.dispatch(gestureDescription, GestureType.TAP)
            }
        } catch (e: Exception) {
            android.util.Log.e("GestureManager", "Error performing tap", e)
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
                gestureDispatcher.dispatch(gestureDescription, GestureType.DOUBLE_TAP)
            }
        } catch (e: Exception) {
            android.util.Log.e("GestureManager", "Error performing double tap", e)
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
                gestureDispatcher.dispatch(gestureDescription, GestureType.TAP_AND_HOLD)
            }
        } catch (e: Exception) {
            android.util.Log.e("GestureManager", "Error performing tap and hold", e)
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
     * Performs a custom gesture action.
     *
     * @param gestureData The GestureData to perform.
     * @return True if the gesture was performed, false otherwise.
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
     * Starts a drag gesture.
     */
    fun startDragGesture() {
        linearGesturePerformer.startGesture(GestureType.DRAG)
    }

    /**
     * Starts a hold and drag gesture.
     */
    fun startHoldAndDragGesture() {
        linearGesturePerformer.startGesture(GestureType.HOLD_AND_DRAG)
    }

    /**
     * Starts a custom swipe gesture.
     */
    fun startCustomSwipe() {
        linearGesturePerformer.startGesture(GestureType.CUSTOM_SWIPE)
    }

    /**
     * Ends a linear gesture.
     */
    fun endLinearGesture() {
        linearGesturePerformer.endGesture()
    }

    /**
     * Cancels a linear gesture.
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