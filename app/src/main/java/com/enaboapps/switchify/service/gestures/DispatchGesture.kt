package com.enaboapps.switchify.service.gestures

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType

/**
 * Dispatches a gesture with multiple strokes.
 *
 * @param service The AccessibilityService instance to dispatch the gesture with
 * @param startPoint The starting point of the gesture
 * @param endPoint The ending point of the gesture (null for single-point gestures like taps)
 * @param gestureType The type of gesture being performed
 * @param strokes Array of stroke descriptions for the gesture
 * @param onComplete Optional callback for when the gesture is completed
 */
fun dispatchGesture(
    service: AccessibilityService,
    startPoint: PointF,
    endPoint: PointF? = null,
    gestureType: GestureType,
    strokes: Array<GestureDescription.StrokeDescription>,
    onComplete: (() -> Unit)? = null
) {
    try {
        GestureLockManager.getInstance().setLockedGestureData(
            GestureData(gestureType, startPoint, endPoint)
        )

        val gestureDescription = GestureDescription.Builder().apply {
            strokes.forEach { addStroke(it) }
        }.build()

        service.dispatchGesture(
            gestureDescription,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    onComplete?.invoke()
                }
            },
            null
        )
    } catch (e: Exception) {
        // Log.e(TAG, "Error dispatching gesture: ", e)
    }
}

/**
 * Dispatches a single-stroke gesture with the specified parameters.
 *
 * @param service The AccessibilityService instance to dispatch the gesture with
 * @param startPoint The starting point of the gesture
 * @param endPoint The ending point of the gesture (null for single-point gestures like taps)
 * @param gestureType The type of gesture being performed
 * @param duration The duration of the gesture in milliseconds
 * @param onComplete Optional callback for when the gesture is completed
 */
fun dispatchGesture(
    service: AccessibilityService,
    startPoint: PointF,
    endPoint: PointF? = null,
    gestureType: GestureType,
    duration: Long,
    onComplete: (() -> Unit)? = null
) {
    val path = Path()
    path.moveTo(startPoint.x, startPoint.y)
    endPoint?.let { path.lineTo(it.x, it.y) }

    val stroke = GestureDescription.StrokeDescription(path, 0, duration)
    dispatchGesture(service, startPoint, endPoint, gestureType, arrayOf(stroke), onComplete)
}