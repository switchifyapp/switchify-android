package com.enaboapps.switchify.service.gestures

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.visuals.ZoomVisual
import com.enaboapps.switchify.service.utils.ScreenUtils

object ZoomGesturePerformer {

    private const val TAG = "ZoomGesturePerformer"
    private const val DEFAULT_ZOOM_DURATION = 500L // Adjusted duration in milliseconds
    private const val ZOOM_AMOUNT_DP = 300 // Zoom amount in density-independent pixels (dp)
    private const val VERTICAL_OFFSET_DP = 100 // Vertical offset for natural gesture
    private const val VISUAL_CIRCLE_SIZE_DP = 120 // Size of the visual circle in dp

    private var zoomVisual: ZoomVisual? = null

    /**
     * Perform a zoom action.
     *
     * @param type The type of zoom action (ZOOM_IN or ZOOM_OUT).
     * @param accessibilityService The accessibility service used to dispatch gestures.
     */
    fun performZoomAction(type: GestureType, accessibilityService: AccessibilityService) {
        // Initialize zoom visual if needed
        if (zoomVisual == null) {
            zoomVisual = ZoomVisual(accessibilityService)
        }

        // Retrieve the center point for the zoom gesture
        val centerPoint = getCenterPoint(
            ScreenUtils.getWidth(accessibilityService),
            ScreenUtils.getHeight(accessibilityService)
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
            DEFAULT_ZOOM_DURATION,
            type == GestureType.ZOOM_IN
        )

        // Calculate vertical offset for more natural finger placement
        val verticalOffsetPx = (VERTICAL_OFFSET_DP * density).toInt()
        Log.d(TAG, "Vertical Offset (px): $verticalOffsetPx")

        // Initialize gesture paths for the two fingers
        val path1 = Path()
        val path2 = Path()

        val halfZoomAmount = zoomAmountPx / 2

        val leftZoomPoint = centerPoint.x - halfZoomAmount
        val rightZoomPoint = centerPoint.x + halfZoomAmount

        // Define gesture paths based on the type of zoom action
        when (type) {
            GestureType.ZOOM_IN -> {
                // Fingers move outward from the center for zooming in
                path1.moveTo(centerPoint.x.toFloat(), (centerPoint.y - verticalOffsetPx).toFloat())
                path1.lineTo(leftZoomPoint.toFloat(), (centerPoint.y - verticalOffsetPx).toFloat())

                path2.moveTo(centerPoint.x.toFloat(), (centerPoint.y + verticalOffsetPx).toFloat())
                path2.lineTo(rightZoomPoint.toFloat(), (centerPoint.y + verticalOffsetPx).toFloat())
            }

            GestureType.ZOOM_OUT -> {
                // Fingers move inward towards the center for zooming out
                path1.moveTo(leftZoomPoint.toFloat(), (centerPoint.y - verticalOffsetPx).toFloat())
                path1.lineTo(centerPoint.x.toFloat(), (centerPoint.y - verticalOffsetPx).toFloat())

                path2.moveTo(rightZoomPoint.toFloat(), (centerPoint.y + verticalOffsetPx).toFloat())
                path2.lineTo(centerPoint.x.toFloat(), (centerPoint.y + verticalOffsetPx).toFloat())
            }

            else -> {
                Log.e(TAG, "performZoomAction: Invalid zoom type: $type")
                return
            }
        }

        // Create stroke descriptions for both fingers
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, DEFAULT_ZOOM_DURATION)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, DEFAULT_ZOOM_DURATION)

        // Dispatch the gesture using our utility function
        dispatchGesture(
            accessibilityService,
            centerPoint,
            null,
            type,
            arrayOf(stroke1, stroke2)
        ) {
            Log.d(TAG, "Gesture Completed Successfully")
            zoomVisual?.stop()
        }
    }

    /**
     * Get the correct point for the center of the zoom gesture.
     * Calculate if there is enough space to perform the zoom action.
     * If not, take away the amount of space needed to perform the action.
     *
     * @param screenWidth The width of the screen.
     * @param screenHeight The height of the screen.
     * @return The point for the center of the zoom gesture.
     */
    private fun getCenterPoint(screenWidth: Int, screenHeight: Int): PointF {
        val centerPoint = GesturePoint.getPoint()
        var x = centerPoint.x
        var y = centerPoint.y

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