package com.enaboapps.switchify.service.techniques.pointscan.blocks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.scanning.ScanVisualConstants
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * Draws the whole point-scan grid in one pass: hairline interior lines and a
 * rounded outline around the block area, each with a light contrast halo so
 * the grid reads over both light and dark apps.
 *
 * Extends [RelativeLayout] only because the overlay base accepts view groups.
 */
internal class PointScanGridView(context: Context) : RelativeLayout(context) {
    var gridSize = 4
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val linePx = ScreenUtils.dpToPxFloat(context, ScanVisualConstants.GRID_LINE_DP.toFloat())
    private val haloPx = ScreenUtils.dpToPxFloat(context, ScanVisualConstants.GRID_LINE_HALO_DP.toFloat())
    private val cornerRadius = ScreenUtils.dpToPxFloat(context, ScanVisualConstants.CORNER_RADIUS_DP)
    private val haloColor = Color.argb(ScanVisualConstants.HALO_ALPHA, 255, 255, 255)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val area = RectF()

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gridSize <= 0 || width <= 0 || height <= 0) return
        val right = PointScanGridGeometry.blockWidth(width, gridSize) * gridSize
        val bottom = PointScanGridGeometry.blockHeight(height, gridSize) * gridSize
        val xs = PointScanGridGeometry.lineOffsets(width, gridSize)
        val ys = PointScanGridGeometry.lineOffsets(height, gridSize)

        // Halo first so the structural line sits on top of it.
        drawGrid(canvas, xs, ys, right, bottom, haloColor, linePx + 2 * haloPx)
        drawGrid(canvas, xs, ys, right, bottom, ScanVisualConstants.STRUCTURAL_COLOR, linePx)
    }

    private fun drawGrid(
        canvas: Canvas,
        xs: List<Int>,
        ys: List<Int>,
        right: Int,
        bottom: Int,
        color: Int,
        strokeWidth: Float
    ) {
        paint.color = color
        paint.strokeWidth = strokeWidth
        val bottomF = bottom.toFloat()
        val rightF = right.toFloat()
        xs.forEach { x ->
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), bottomF, paint)
        }
        ys.forEach { y ->
            canvas.drawLine(0f, y.toFloat(), rightF, y.toFloat(), paint)
        }
        val half = strokeWidth / 2f
        area.set(half, half, rightF - half, bottomF - half)
        canvas.drawRoundRect(area, cornerRadius, cornerRadius, paint)
    }
}
