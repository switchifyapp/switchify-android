package com.enaboapps.switchify.service.techniques.radar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.techniques.shared.ScanMethodUIConstants
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadarUI(private val context: Context) {
    private var radarLineContainer: FrameLayout? = null
    private var radarLine: RadarLineView? = null
    private var radarCircle: RelativeLayout? = null

    private val window = SwitchifyAccessibilityWindow.instance

    companion object {
        const val RADAR_CIRCLE_SIZE = 50
        private const val RADAR_ALPHA = 0.7f
    }

    private val screenWidth: Int
        get() = ScreenUtils.getWidth(context)

    private val screenHeight: Int
        get() = ScreenUtils.getHeight(context)

    fun showRadarLine(angle: Float) {
        if (radarLineContainer == null) {
            createRadarLine()
        }
        updateRadarLine(angle)
    }

    fun showRadarCircle(x: Int, y: Int) {
        if (radarCircle == null) {
            createRadarCircle(x, y)
        } else {
            updateRadarCircle(x, y)
        }
    }

    private fun createRadarLine() {
        radarLine = RadarLineView(context).apply {
            val color = ScanColorManager.getScanColorSetFromPreferences(context).primaryColor
            setColor(Color.parseColor(color))
        }
        radarLineContainer = FrameLayout(context).apply {
            addView(radarLine)
        }
        radarLineContainer?.let {
            window.addView(it, 0, 0, screenWidth, screenHeight)
        }
    }

    private fun createRadarCircle(x: Int, y: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor))
            alpha = (RADAR_ALPHA * 255).toInt()
        }
        radarCircle = RelativeLayout(context).apply {
            background = drawable
        }
        updateRadarCircle(x, y)
    }

    private fun updateRadarLine(angle: Float) {
        radarLine?.updateAngle(angle)
    }

    private fun updateRadarCircle(x: Int, y: Int) {
        radarCircle?.let {
            if (it.parent == null) {
                window.addView(
                    it,
                    x - RADAR_CIRCLE_SIZE / 2,
                    y - RADAR_CIRCLE_SIZE / 2,
                    RADAR_CIRCLE_SIZE,
                    RADAR_CIRCLE_SIZE
                )
            } else {
                window.updateViewLayout(
                    it,
                    x - RADAR_CIRCLE_SIZE / 2,
                    y - RADAR_CIRCLE_SIZE / 2
                )
            }
        }
    }

    fun removeRadarLine() {
        radarLineContainer?.let {
            window.removeView(it)
            radarLineContainer = null
            radarLine = null
        }
    }

    fun removeRadarCircle() {
        radarCircle?.let {
            window.removeView(it)
            radarCircle = null
        }
    }

    fun reset() {
        removeRadarLine()
        removeRadarCircle()
    }

    private inner class RadarLineView(context: Context) : View(context) {
        private val paint = Paint().apply {
            strokeWidth = ScanMethodUIConstants.LINE_THICKNESS.toFloat()
            style = Paint.Style.STROKE
        }
        private var currentAngle = 0f

        fun setColor(color: Int) {
            paint.color = color
            paint.alpha = (RADAR_ALPHA * 255).toInt()
        }

        fun updateAngle(angle: Float) {
            currentAngle = angle
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val maxLength = sqrt((width * width + height * height) / 4f)
            val endX = centerX + maxLength * cos(Math.toRadians(currentAngle.toDouble())).toFloat()
            val endY = centerY + maxLength * sin(Math.toRadians(currentAngle.toDouble())).toFloat()
            canvas.drawLine(centerX, centerY, endX, endY, paint)
        }
    }
}