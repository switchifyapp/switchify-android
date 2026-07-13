package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.RelativeLayout

internal object NumberedTouchPointView {
    fun create(context: Context, number: Int, size: Int): RelativeLayout {
        val view = TouchPoint(context, number).apply {
            layoutParams = RelativeLayout.LayoutParams(size, size)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        return RelativeLayout(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(view)
        }
    }

    private class TouchPoint(context: Context, private val number: Int) : View(context) {
        private val tokens = GestureVisualTokens(context)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.primary
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.onPrimary
            textAlign = Paint.Align.CENTER
            textSize = tokens.labelText
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        override fun onDraw(canvas: Canvas) {
            val radius = minOf(width, height) / 2f
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(
                cx + tokens.shadowOffset,
                cy + tokens.shadowOffset,
                radius - tokens.shadowOffset,
                shadowPaint
            )
            canvas.drawCircle(cx, cy, radius - tokens.shadowOffset, fillPaint)
            val metrics = labelPaint.fontMetrics
            canvas.drawText(number.toString(), cx, cy - (metrics.ascent + metrics.descent) / 2f, labelPaint)
        }
    }
}
