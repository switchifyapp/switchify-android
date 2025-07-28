package com.enaboapps.switchify.service.gestures

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
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
    companion object {
        /**
         * The minimum delay between consecutive gestures in milliseconds.
         */
        private const val GESTURE_DELAY_MS = 500L
    }

    /**
     * The current point visual
     */
    private val gestureVisualManager = GestureVisualManager(accessibilityService)

    /**
     * Represents the current state of a gesture.
     *
     * @property startPoint The starting point of the gesture.
     * @property isPerforming Whether a gesture is currently being performed.
     * @property currentType The type of the current gesture.
     */
    private data class GestureState(
        var startPoint: PointF? = null,
        var isPerforming: Boolean = false,
        var currentType: GestureType? = null
    )

    /**
     * The current state of the gesture being performed.
     */
    private val gestureState = GestureState()

    /**
     * The timestamp of the last performed gesture.
     */
    private var lastGestureTime: Long = 0

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
        if (gestureState.isPerforming) return

        gestureState.apply {
            startPoint = startingPoint ?: GesturePoint.getPoint()
            isPerforming = true
            currentType = type
        }

        if (showMessage) {
            showGestureMessage(type)
        }
        gestureVisualManager.showStaticCircle(
            gestureState.startPoint?.x?.toInt() ?: GesturePoint.x,
            gestureState.startPoint?.y?.toInt() ?: GesturePoint.y
        )
    }

    /**
     * Ends the current gesture and performs it.
     *
     * @param endPoint The end point of the gesture.
     */
    fun endGesture(endPoint: PointF? = null) {
        val (startPoint, _, gestureType) = gestureState
        if (startPoint == null || gestureType == null) {
            resetGestureState()
            return
        }

        val endPoint = endPoint ?: calculateEndPoint(gestureType, startPoint)
        performGesture(gestureType, startPoint, endPoint)
        gestureLockManager.setLockedGestureData(GestureData(gestureType, startPoint, endPoint))

        resetGestureState()
        gestureVisualManager.hideCircle()
    }

    /**
     * Cancels the current gesture.
     */
    fun cancelGesture() {
        resetGestureState()
        gestureVisualManager.hideCircle()
    }

    /**
     * Resets the gesture state to its initial values.
     */
    private fun resetGestureState() {
        gestureState.apply {
            startPoint = null
            isPerforming = false
            currentType = null
        }
    }

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
        if (!checkGestureDelay()) return

        try {
            showVisualFeedback(start, end, type)

            when (type) {
                GestureType.HOLD_AND_DRAG -> {
                    // Create hold stroke
                    val holdPath = Path().apply { moveTo(start.x, start.y) }
                    val holdStroke = GestureDescription.StrokeDescription(
                        holdPath,
                        0,
                        GestureData.HOLD_BEFORE_DRAG_DURATION
                    )

                    // Create drag stroke
                    val dragPath = Path().apply {
                        moveTo(start.x, start.y)
                        lineTo(end.x, end.y)
                    }
                    val dragStroke = GestureDescription.StrokeDescription(
                        dragPath,
                        GestureData.HOLD_BEFORE_DRAG_DURATION - 5,
                        GestureData.DRAG_DURATION
                    )

                    // Dispatch both strokes together
                    dispatchGesture(
                        accessibilityService,
                        start,
                        end,
                        type,
                        arrayOf(holdStroke, dragStroke)
                    )
                }

                else -> {
                    val duration = getDurationForGestureType(type)
                    dispatchGesture(accessibilityService, start, end, type, duration)
                }
            }

            lastGestureTime = System.currentTimeMillis()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Checks if enough time has passed since the last gesture to perform a new one.
     *
     * @return True if enough time has passed, false otherwise.
     */
    private fun checkGestureDelay(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastGestureTime >= GESTURE_DELAY_MS
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
     * Dispatches the gesture to the Android system.
     *
     * @param gestureDescription The GestureDescription to dispatch.
     */
    private fun dispatchGesture(gestureDescription: GestureDescription) {
        accessibilityService.dispatchGesture(
            gestureDescription,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    // Handle completion if needed
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    // Handle cancellation if needed
                }
            },
            null
        )
    }

    /**
     * Checks if a gesture is currently being performed.
     *
     * @return True if a gesture is in progress, false otherwise.
     */
    fun isPerformingGesture(): Boolean = gestureState.isPerforming

    /**
     * Cancels any ongoing gestures and resets the gesture state.
     */
    fun cancelOngoingGestures() {
        resetGestureState()
    }
}