package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal class GesturePathOverlayView(
    context: Context,
    private val start: PointF,
    private val end: PointF,
    private val fingerLabel: Int? = null
) : View(context) {
    private val tokens = GestureVisualTokens(context)
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val underlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        strokeWidth = tokens.pathUnderlay
        strokeCap = Paint.Cap.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(tokens.primary, 64)
        strokeWidth = tokens.pathStroke
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.primary
        strokeWidth = tokens.pathStroke
        strokeCap = Paint.Cap.ROUND
    }
    private val headShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 0, 0)
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.primary
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.onPrimary
        strokeWidth = tokens.dp(2f).toFloat()
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
        textAlign = Paint.Align.CENTER
        textSize = tokens.labelText
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        val currentX = start.x + (end.x - start.x) * progress
        val currentY = start.y + (end.y - start.y) * progress
        canvas.drawLine(start.x, start.y, end.x, end.y, underlayPaint)
        canvas.drawLine(start.x, start.y, end.x, end.y, trackPaint)
        canvas.drawLine(start.x, start.y, currentX, currentY, progressPaint)

        val radius = tokens.pathHead / 2f
        canvas.drawCircle(
            currentX + tokens.shadowOffset,
            currentY + tokens.shadowOffset,
            radius,
            headShadowPaint
        )
        canvas.drawCircle(currentX, currentY, radius, headPaint)
        if (fingerLabel != null) {
            val metrics = detailPaint.fontMetrics
            val baseline = currentY - (metrics.ascent + metrics.descent) / 2f
            detailPaint.style = Paint.Style.FILL
            canvas.drawText(fingerLabel.toString(), currentX, baseline, detailPaint)
        } else {
            drawChevron(canvas, currentX, currentY, radius)
        }
    }

    private fun drawChevron(canvas: Canvas, x: Float, y: Float, radius: Float) {
        val angle = atan2(end.y - start.y, end.x - start.x)
        val tipX = x + cos(angle) * radius * 0.48f
        val tipY = y + sin(angle) * radius * 0.48f
        val wingDistance = radius * 0.5f
        val wingAngle = 2.45f
        detailPaint.style = Paint.Style.STROKE
        canvas.drawLine(
            tipX,
            tipY,
            x + cos(angle + wingAngle) * wingDistance,
            y + sin(angle + wingAngle) * wingDistance,
            detailPaint
        )
        canvas.drawLine(
            tipX,
            tipY,
            x + cos(angle - wingAngle) * wingDistance,
            y + sin(angle - wingAngle) * wingDistance,
            detailPaint
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
