package com.enaboapps.switchify.service.gestures

import android.accessibilityservice.AccessibilityService
import android.graphics.PointF
import android.util.Log
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.execution.GestureDispatcher
import com.enaboapps.switchify.service.gestures.execution.GesturePathBuilder
import com.enaboapps.switchify.service.gestures.visuals.PinchVisual
import com.enaboapps.switchify.service.utils.ScreenUtils

object PinchGesturePerformer {

    private const val TAG = "PinchGesturePerformer"
    private const val PINCH_AMOUNT_DP = 300 // Pinch amount in density-independent pixels (dp)
    private const val VISUAL_CIRCLE_SIZE_DP = 120 // Size of the visual circle in dp

    private var pinchVisual: PinchVisual? = null

    /**
     * Perform a pinch action using the unified gesture execution pipeline.
     *
     * @param type The type of pinch action (PINCH_IN or PINCH_OUT).
     * @param accessibilityService The accessibility service used to dispatch gestures.
     * @param point The starting point of the gesture.
     */
    fun performPinchAction(
        type: GestureType,
        accessibilityService: AccessibilityService,
        point: PointF
    ) {
        val gestureDispatcher =
            GestureDispatcher(accessibilityService as SwitchifyAccessibilityService)

        // Validate pinch type
        if (type != GestureType.PINCH_IN && type != GestureType.PINCH_OUT) {
            Log.e(TAG, "performPinchAction: Invalid pinch type: $type")
            return
        }

        // Initialize pinch visual if needed
        if (pinchVisual == null) {
            pinchVisual = PinchVisual(accessibilityService)
        }

        // Retrieve the center point for the pinch gesture
        val centerPoint = getCenterPoint(
            ScreenUtils.getWidth(accessibilityService),
            ScreenUtils.getHeight(accessibilityService),
            point
        )
        Log.d(TAG, "Center Point: (${centerPoint.x}, ${centerPoint.y})")

        // Calculate pinch amount based on screen density
        val density = accessibilityService.resources.displayMetrics.density
        val pinchAmountPx = (PINCH_AMOUNT_DP * density).toInt()
        val visualCircleSize = (VISUAL_CIRCLE_SIZE_DP * density)
        Log.d(TAG, "Pinch Amount (px): $pinchAmountPx")

        // Show visual feedback
        pinchVisual?.start(
            centerPoint.x.toFloat(),
            centerPoint.y.toFloat(),
            visualCircleSize,
            GestureData.PINCH_DURATION,
            type == GestureType.PINCH_OUT
        )

        // Create gesture description using unified path builder
        val gestureDescription = GesturePathBuilder.createPinchPath(
            centerPoint,
            type == GestureType.PINCH_OUT,
            GestureData.PINCH_DURATION
        )

        // Dispatch using unified dispatcher with custom result handling
        gestureDispatcher.dispatchWithActions(
            gestureDescription,
            type,
            onCompleted = {
                Log.d(TAG, "Gesture Completed Successfully")
            },
            onCancelled = {
                Log.e(TAG, "Gesture Cancelled")
                pinchVisual?.stop()
            },
            onError = { error ->
                Log.e(TAG, "Gesture dispatch error", error)
                pinchVisual?.stop()
            }
        )

        Log.d(TAG, "Gesture dispatched: $type")
    }

    /**
     * Get the correct point for the center of the pinch gesture.
     * Calculate if there is enough space to perform the pinch action.
     * If not, take away the amount of space needed to perform the action.
     *
     * @param screenWidth The width of the screen.
     * @param screenHeight The height of the screen.
     * @param point The starting point of the gesture.
     * @return The point for the center of the pinch gesture.
     */
    private fun getCenterPoint(screenWidth: Int, screenHeight: Int, point: PointF): PointF {
        var x = point.x
        var y = point.y

        if (x + PINCH_AMOUNT_DP * 2 > screenWidth) {
            x = (screenWidth - PINCH_AMOUNT_DP * 2).toFloat()
        } else if (x - PINCH_AMOUNT_DP * 2 < 0) {
            x = PINCH_AMOUNT_DP * 2.toFloat()
        }
        if (y + PINCH_AMOUNT_DP * 2 > screenHeight) {
            y = (screenHeight - PINCH_AMOUNT_DP * 2).toFloat()
        } else if (y - PINCH_AMOUNT_DP * 2 < 0) {
            y = PINCH_AMOUNT_DP * 2.toFloat()
        }

        return PointF(x, y)
    }
}
