package com.enaboapps.switchify.service.gestures

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureData.Companion.DOUBLE_TAP_INTERVAL
import com.enaboapps.switchify.service.gestures.data.GestureData.Companion.TAP_AND_HOLD_DURATION
import com.enaboapps.switchify.service.gestures.data.GestureData.Companion.TAP_DURATION
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.visuals.GestureDrawing
import com.enaboapps.switchify.service.methods.nodes.NodeExaminer

/**
 * The GestureManager class is responsible for managing and performing various gesture actions
 * in the Switchify accessibility service. It handles taps, swipes, drags, and custom gestures.
 */
class GestureManager private constructor() {
    companion object {
        private var instance: GestureManager? = null

        /**
         * Gets the singleton instance of the GestureManager.
         *
         * @return The GestureManager instance.
         */
        fun getInstance(): GestureManager {
            if (instance == null) {
                instance = GestureManager()
            }
            return instance!!
        }
    }

    private var accessibilityService: SwitchifyAccessibilityService? = null
    private var preferenceManager: PreferenceManager? = null
    private lateinit var linearGesturePerformer: LinearGesturePerformer

    /**
     * Sets up the GestureManager with the necessary components.
     *
     * @param accessibilityService The SwitchifyAccessibilityService instance.
     */
    fun setup(accessibilityService: SwitchifyAccessibilityService) {
        this.accessibilityService = accessibilityService
        AutoScrollManager.getInstance().init(accessibilityService)
        preferenceManager = PreferenceManager(accessibilityService)
        linearGesturePerformer =
            LinearGesturePerformer(accessibilityService, GestureLockManager.getInstance())
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
                val path = Path()
                var point = if (x != null && y != null) {
                    PointF(x.toFloat(), y.toFloat())
                } else {
                    getAssistedCurrentPoint()
                }
                val gestureDrawing = GestureDrawing(it)
                gestureDrawing.drawCircleAndRemove(
                    point.x.toInt(),
                    point.y.toInt(),
                    TAP_DURATION
                )
                path.moveTo(point.x, point.y)
                GestureLockManager.getInstance().setLockedGestureData(
                    GestureData(
                        GestureType.TAP,
                        point
                    )
                )
                val gestureDescription = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION)).build()
                it.dispatchGesture(
                    gestureDescription,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                        }
                    },
                    null
                )
            }
        } catch (e: Exception) {
            // Log.e(TAG, "onTap: ", e)
        }
    }

    /**
     * Performs a double tap gesture at the current point.
     */
    fun performDoubleTap() {
        try {
            accessibilityService?.let {
                val path = Path()
                val point = getAssistedCurrentPoint()
                val gestureDrawing = GestureDrawing(it)
                gestureDrawing.drawCircleAndRemove(
                    point.x.toInt(),
                    point.y.toInt(),
                    TAP_DURATION
                )
                path.moveTo(point.x, point.y)
                GestureLockManager.getInstance().setLockedGestureData(
                    GestureData(
                        GestureType.DOUBLE_TAP,
                        point
                    )
                )
                val tap1 = GestureDescription.StrokeDescription(path, 0, TAP_DURATION)
                val tap2 =
                    GestureDescription.StrokeDescription(path, DOUBLE_TAP_INTERVAL, TAP_DURATION)
                val gestureDescription = GestureDescription.Builder()
                    .addStroke(tap1)
                    .addStroke(tap2)
                    .build()
                it.dispatchGesture(
                    gestureDescription,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                        }
                    },
                    null
                )
            }
        } catch (e: Exception) {
            // Log.e(TAG, "onDoubleTap: ", e)
        }
    }

    /**
     * Performs a tap and hold gesture at the current point.
     */
    fun performTapAndHold() {
        try {
            accessibilityService?.let {
                val path = Path()
                val point = getAssistedCurrentPoint()
                val gestureDrawing = GestureDrawing(it)
                gestureDrawing.drawCircleAndRemove(
                    point.x.toInt(),
                    point.y.toInt(),
                    TAP_AND_HOLD_DURATION
                )
                path.moveTo(point.x, point.y)
                GestureLockManager.getInstance().setLockedGestureData(
                    GestureData(
                        GestureType.TAP_AND_HOLD,
                        point
                    )
                )
                val gestureDescription = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_AND_HOLD_DURATION))
                    .build()
                it.dispatchGesture(
                    gestureDescription,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                        }
                    },
                    null
                )
            }
        } catch (e: Exception) {
            // Log.e(TAG, "onTapAndHold: ", e)
        }
    }

    /**
     * Performs the gesture lock action if the gesture lock is enabled.
     *
     * @return True if a locked gesture action was performed, false otherwise.
     */
    fun performGestureLockAction(): Boolean {
        if (GestureLockManager.getInstance().isGestureLockEnabled() == true) {
            GestureLockManager.getInstance().getLockedGestureData()?.let { gestureData ->
                if (GestureLockManager.getInstance()
                        .canLockGesture(gestureData.gestureType) == true
                ) {
                    return gestureData.performLockAction(this)
                }
            }
        }
        return false
    }

    /**
     * Toggles the gesture lock on or off.
     */
    fun toggleGestureLock() {
        GestureLockManager.getInstance().toggleGestureLock()
    }

    /**
     * Checks if the gesture lock is currently enabled.
     *
     * @return True if the gesture lock is enabled, false otherwise.
     */
    fun isGestureLockEnabled(): Boolean {
        return GestureLockManager.getInstance().isGestureLockEnabled() == true
    }

    /**
     * Performs a swipe or scroll action.
     *
     * @param type The GestureType of the swipe.
     */
    fun performSwipeOrScroll(type: GestureType) {
        if (AutoScrollManager.getInstance()
                .startAutoScroll(GestureData(type, GesturePoint.getPoint()))
        ) return
        linearGesturePerformer.startGesture(type)
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
        return linearGesturePerformer.isPerformingGesture()
    }

    /**
     * Performs a zoom action.
     *
     * @param type The type of zoom action to perform.
     */
    fun performZoom(type: GestureType) {
        GestureLockManager.getInstance().setLockedGestureData(
            GestureData(
                type,
                GesturePoint.getPoint()
            )
        )
        accessibilityService?.let {
            ZoomGesturePerformer.performZoomAction(type, it)
        }
    }
}