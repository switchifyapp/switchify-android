package com.enaboapps.switchify.service.techniques.radar.sweep

import android.graphics.Rect
import kotlin.math.sqrt

/**
 * The area a radar sweep covers and the pivot the line swings around. The pivot
 * sits in the middle of the top or bottom edge of [bounds], so the same maths
 * serves the whole screen and a single grid block.
 */
data class RadarSweepGeometry(
    val bounds: Rect,
    val pivotFromTop: Boolean
) {
    val pivotX: Float
        get() = (bounds.left + bounds.right) / 2f

    val pivotY: Float
        get() = if (pivotFromTop) bounds.top.toFloat() else bounds.bottom.toFloat()

    /** Distance from the pivot to the farthest corner of [bounds]. */
    val maxDistance: Float
        get() {
            val halfWidth = (bounds.right - bounds.left) / 2f
            val height = (bounds.bottom - bounds.top).toFloat()
            return sqrt(halfWidth * halfWidth + height * height)
        }
}
