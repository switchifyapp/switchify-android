package com.enaboapps.switchify.service.techniques.pointscan.blocks

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class PointScanBlockDrawable : Drawable() {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK // Solid black for consistent appearance
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK // Solid black for consistent appearance
        style = Paint.Style.FILL
    }

    private val path = Path()
    private val cornerRadius = 60f

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        val strokeHalf = strokePaint.strokeWidth / 2

        // Adjust for stroke width
        val adjustedLeft = left + strokeHalf
        val adjustedTop = top + strokeHalf
        val adjustedRight = right - strokeHalf
        val adjustedBottom = bottom - strokeHalf

        // Draw square edges that stop where rounded corners begin
        val inset = cornerRadius

        // Top edge (left side, stopping before top-right corner)
        canvas.drawLine(
            adjustedLeft + inset,
            adjustedTop,
            adjustedRight - inset,
            adjustedTop,
            strokePaint
        )
        // Right edge (top side, stopping before bottom-right corner)
        canvas.drawLine(
            adjustedRight,
            adjustedTop + inset,
            adjustedRight,
            adjustedBottom - inset,
            strokePaint
        )
        // Bottom edge (right side, stopping before bottom-left corner)
        canvas.drawLine(
            adjustedRight - inset,
            adjustedBottom,
            adjustedLeft + inset,
            adjustedBottom,
            strokePaint
        )
        // Left edge (bottom side, stopping before top-left corner)
        canvas.drawLine(
            adjustedLeft,
            adjustedBottom - inset,
            adjustedLeft,
            adjustedTop + inset,
            strokePaint
        )

        // Fill the corner areas with solid color
        // Top-left corner area
        path.reset()
        path.moveTo(adjustedLeft, adjustedTop + inset)
        path.lineTo(adjustedLeft, adjustedTop)
        path.lineTo(adjustedLeft + inset, adjustedTop)
        path.quadTo(adjustedLeft, adjustedTop, adjustedLeft, adjustedTop + inset)
        path.close()
        canvas.drawPath(path, fillPaint)

        // Top-right corner area
        path.reset()
        path.moveTo(adjustedRight - inset, adjustedTop)
        path.lineTo(adjustedRight, adjustedTop)
        path.lineTo(adjustedRight, adjustedTop + inset)
        path.quadTo(adjustedRight, adjustedTop, adjustedRight - inset, adjustedTop)
        path.close()
        canvas.drawPath(path, fillPaint)

        // Bottom-right corner area
        path.reset()
        path.moveTo(adjustedRight, adjustedBottom - inset)
        path.lineTo(adjustedRight, adjustedBottom)
        path.lineTo(adjustedRight - inset, adjustedBottom)
        path.quadTo(adjustedRight, adjustedBottom, adjustedRight, adjustedBottom - inset)
        path.close()
        canvas.drawPath(path, fillPaint)

        // Bottom-left corner area
        path.reset()
        path.moveTo(adjustedLeft + inset, adjustedBottom)
        path.lineTo(adjustedLeft, adjustedBottom)
        path.lineTo(adjustedLeft, adjustedBottom - inset)
        path.quadTo(adjustedLeft, adjustedBottom, adjustedLeft + inset, adjustedBottom)
        path.close()
        canvas.drawPath(path, fillPaint)

        // Draw rounded corner outlines
        // Top-left rounded corner
        path.reset()
        path.moveTo(adjustedLeft, adjustedTop + inset)
        path.quadTo(adjustedLeft, adjustedTop, adjustedLeft + inset, adjustedTop)
        canvas.drawPath(path, strokePaint)

        // Top-right rounded corner
        path.reset()
        path.moveTo(adjustedRight - inset, adjustedTop)
        path.quadTo(adjustedRight, adjustedTop, adjustedRight, adjustedTop + inset)
        canvas.drawPath(path, strokePaint)

        // Bottom-right rounded corner
        path.reset()
        path.moveTo(adjustedRight, adjustedBottom - inset)
        path.quadTo(adjustedRight, adjustedBottom, adjustedRight - inset, adjustedBottom)
        canvas.drawPath(path, strokePaint)

        // Bottom-left rounded corner
        path.reset()
        path.moveTo(adjustedLeft + inset, adjustedBottom)
        path.quadTo(adjustedLeft, adjustedBottom, adjustedLeft, adjustedBottom - inset)
        canvas.drawPath(path, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        strokePaint.alpha = alpha
        fillPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        strokePaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}