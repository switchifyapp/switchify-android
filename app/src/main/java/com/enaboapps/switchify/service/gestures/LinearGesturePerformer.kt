package com.enaboapps.switchify.service.gestures

import android.graphics.PointF
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.GestureStateManager
import com.enaboapps.switchify.service.gestures.execution.GestureDispatcher
import com.enaboapps.switchify.service.gestures.execution.GesturePathBuilder
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualManager
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.ServiceMessageHUD

/**
 * A class responsible for performing linear gestures in an Android Accessibility Service.
 *
 * This class handles various types of gestures including swipes, drags, and hold-and-drag gestures.
 * It manages the gesture lifecycle, provides visual feedback, and interacts with the Android
 * Accessibility Service to dispatch gestures.
 *
 * @property accessibilityService The accessibility service used to dispatch gestures.
 * @property gestureLockManager The manager responsible for locking gestures.
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
     * Starts a new gesture of the specified type.
     *
     * @param type The type of gesture to start.
     * @param showMessage Whether to show a message for the gesture.
     * @param startingPoint The starting point of the gesture.
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
        Log.d(TAG, "GestureStateManager state after start: ${GestureStateManager.getStateSummary()}")

        if (showMessage) {
            showGestureMessage(type)
        }
        gestureVisualManager.showStaticCircle(
            startPoint.x.toInt(),
            startPoint.y.toInt()
        )
    }

    /**
     * Ends the current gesture and performs it.
     *
     * @param endPoint The end point of the gesture.
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
        gestureLockManager.setLockedGestureData(GestureData(gestureType, startPoint, calculatedEndPoint))

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
     * Performs the gesture with the given parameters.
     *
     * @param type The type of gesture to perform.
     * @param start The starting point of the gesture.
     * @param end The ending point of the gesture.
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