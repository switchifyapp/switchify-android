package com.enaboapps.switchify.service.gestures

import android.graphics.PointF

/**
 * This object represents the current point position used for gestures
 */
object GesturePoint {
    /**
     * This is the x (horizontal) position of the gesture point
     */
    var x = 0

    /**
     * This is the y (vertical) position of the gesture point
     */
    var y = 0

    /**
     * This function returns the current gesture point
     * @return The gesture point
     */
    fun getPoint(): PointF {
        return PointF(x.toFloat(), y.toFloat())
    }
}
