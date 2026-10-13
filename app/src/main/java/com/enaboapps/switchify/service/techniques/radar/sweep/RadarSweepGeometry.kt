package com.enaboapps.switchify.service.techniques.radar.sweep

import android.graphics.Rect
import kotlin.math.sqrt

enum class RadarPivot { TOP, BOTTOM, CENTER }

/**
 * The area a radar sweep covers and the pivot the line swings around. The pivot
 * sits in the middle of the top or bottom edge of [bounds], or at its centre, so
 * the same maths serves the whole screen and a single grid block.
 */
data class RadarSweepGeometry(
    val bounds: Rect,
    val pivot: RadarPivot
) {
    val pivotX: Float
        get() = (bounds.left + bounds.right) / 2f

    val pivotY: Float
        get() = when (pivot) {
            RadarPivot.TOP -> bounds.top.toFloat()
            RadarPivot.BOTTOM -> bounds.bottom.toFloat()
            RadarPivot.CENTER -> (bounds.top + bounds.bottom) / 2f
        }

    /** Distance from the pivot to the farthest corner of [bounds]. */
    val maxDistance: Float
        get() {
            val halfWidth = (bounds.right - bounds.left) / 2f
            val height = (bounds.bottom - bounds.top).toFloat()
            val reach = if (pivot == RadarPivot.CENTER) height / 2f else height
            return sqrt(halfWidth * halfWidth + reach * reach)
        }
}
