package com.enaboapps.switchify.service.gestures

import android.accessibilityservice.AccessibilityService
import android.graphics.PointF
import android.util.Log
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.execution.GestureDispatcher
import com.enaboapps.switchify.service.gestures.execution.GesturePathBuilder
import com.enaboapps.switchify.service.gestures.visuals.ZoomVisual
import com.enaboapps.switchify.service.utils.ScreenUtils

object ZoomGesturePerformer {

    private const val TAG = "ZoomGesturePerformer"
    private const val ZOOM_AMOUNT_DP = 300 // Zoom amount in density-independent pixels (dp)
    private const val VISUAL_CIRCLE_SIZE_DP = 120 // Size of the visual circle in dp

    private var zoomVisual: ZoomVisual? = null

    /**
     * Perform a zoom action using the unified gesture execution pipeline.
     *
     * @param type The type of zoom action (ZOOM_IN or ZOOM_OUT).
     * @param accessibilityService The accessibility service used to dispatch gestures.
     * @param point The starting point of the gesture.
     */
    fun performZoomAction(
        type: GestureType,
        accessibilityService: AccessibilityService,
        point: PointF
    ) {
        val gestureDispatcher = GestureDispatcher(accessibilityService as SwitchifyAccessibilityService)

        // Validate zoom type
        if (type != GestureType.ZOOM_IN && type != GestureType.ZOOM_OUT) {
            Log.e(TAG, "performZoomAction: Invalid zoom type: $type")
            return
        }
        
        // Initialize zoom visual if needed
        if (zoomVisual == null) {
            zoomVisual = ZoomVisual(accessibilityService)
        }

        // Retrieve the center point for the zoom gesture
        val centerPoint = getCenterPoint(
            ScreenUtils.getWidth(accessibilityService),
            ScreenUtils.getHeight(accessibilityService),
            point
        )
        Log.d(TAG, "Center Point: (${centerPoint.x}, ${centerPoint.y})")

        // Calculate zoom amount based on screen density
        val density = accessibilityService.resources.displayMetrics.density
        val zoomAmountPx = (ZOOM_AMOUNT_DP * density).toInt()
        val visualCircleSize = (VISUAL_CIRCLE_SIZE_DP * density)
        Log.d(TAG, "Zoom Amount (px): $zoomAmountPx")

        // Show visual feedback
        zoomVisual?.start(
            centerPoint.x.toFloat(),
            centerPoint.y.toFloat(),
            visualCircleSize,
            GestureData.ZOOM_DURATION,
            type == GestureType.ZOOM_IN
        )

        // Create gesture description using unified path builder
        val zoomAmount = if (type == GestureType.ZOOM_IN) zoomAmountPx.toFloat() else -zoomAmountPx.toFloat()
        val gestureDescription = GesturePathBuilder.createZoomPath(
            centerPoint,
            zoomAmount,
            GestureData.ZOOM_DURATION
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
                zoomVisual?.stop()
            },
            onError = { error ->
                Log.e(TAG, "Gesture dispatch error", error)
                zoomVisual?.stop()
            }
        )
        
        Log.d(TAG, "Gesture dispatched: $type")
    }

    /**
     * Get the correct point for the center of the zoom gesture.
     * Calculate if there is enough space to perform the zoom action.
     * If not, take away the amount of space needed to perform the action.
     *
     * @param screenWidth The width of the screen.
     * @param screenHeight The height of the screen.
     * @param point The starting point of the gesture.
     * @return The point for the center of the zoom gesture.
     */
    private fun getCenterPoint(screenWidth: Int, screenHeight: Int, point: PointF): PointF {
        var x = point.x
        var y = point.y

        if (x + ZOOM_AMOUNT_DP * 2 > screenWidth) {
            x = (screenWidth - ZOOM_AMOUNT_DP * 2).toFloat()
        } else if (x - ZOOM_AMOUNT_DP * 2 < 0) {
            x = ZOOM_AMOUNT_DP * 2.toFloat()
        }
        if (y + ZOOM_AMOUNT_DP * 2 > screenHeight) {
            y = (screenHeight - ZOOM_AMOUNT_DP * 2).toFloat()
        } else if (y - ZOOM_AMOUNT_DP * 2 < 0) {
            y = ZOOM_AMOUNT_DP * 2.toFloat()
        }

        return PointF(x, y)
    }
}