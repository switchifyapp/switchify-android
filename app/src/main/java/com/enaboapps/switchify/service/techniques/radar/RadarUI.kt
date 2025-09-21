package com.enaboapps.switchify.service.techniques.radar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.techniques.shared.ScanMethodUIConstants
import com.enaboapps.switchify.service.utils.ScreenUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadarUI(private val context: Context) : AccessTechniqueUIBase() {
    private var radarLineContainer: FrameLayout? = null
    private var radarLine: RadarLineView? = null
    private var radarCircle: RelativeLayout? = null

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
            setColor(color.toColorInt())
        }
        radarLineContainer = FrameLayout(context).apply {
            addView(radarLine)
        }
        radarLineContainer?.let {
            super.addView(it, 0, 0, screenWidth, screenHeight)
        }
    }

    private fun createRadarCircle(x: Int, y: Int) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor.toColorInt())
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
                super.addView(
                    it,
                    x - RADAR_CIRCLE_SIZE / 2,
                    y - RADAR_CIRCLE_SIZE / 2,
                    RADAR_CIRCLE_SIZE,
                    RADAR_CIRCLE_SIZE
                )
            } else {
                super.updateView(
                    it,
                    x - RADAR_CIRCLE_SIZE / 2,
                    y - RADAR_CIRCLE_SIZE / 2,
                    RADAR_CIRCLE_SIZE,
                    RADAR_CIRCLE_SIZE
                )
            }
        }
    }

    fun removeRadarLine() {
        removeRadarLineSafely()
    }

    fun removeRadarCircle() {
        removeRadarCircleSafely()
    }

    private fun removeRadarLineSafely() {
        radarLineContainer?.let { container ->
            try {
                if (container.parent != null) {
                    super.removeView(container)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                radarLineContainer = null
                radarLine = null
            }
        }
    }

    private fun removeRadarCircleSafely() {
        radarCircle?.let { circle ->
            try {
                if (circle.parent != null) {
                    super.removeView(circle)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                radarCircle = null
            }
        }
    }

    fun reset() {
        try {
            removeRadarLineSafely()
            removeRadarCircleSafely()
            super.hide()
        } catch (e: Exception) {
            // Force cleanup even if removal fails
            forceCleanup()
        }
    }

    private fun forceCleanup() {
        radarLineContainer = null
        radarLine = null
        radarCircle = null
        super.hide()
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
            // Windscreen wiper pivot point - configurable top or bottom
            RadarSettings.init(context)
            val pivotX = width / 2f
            val pivotY =
                if (RadarSettings.getStartingPosition() == RadarSettings.StartingPosition.TOP) {
                    0f  // Top edge
                } else {
                    height.toFloat()  // Bottom edge
                }
            val maxLength = sqrt((width * width + height * height).toFloat())
            val endX = pivotX + maxLength * cos(Math.toRadians(currentAngle.toDouble())).toFloat()
            val endY = pivotY + maxLength * sin(Math.toRadians(currentAngle.toDouble())).toFloat()
            canvas.drawLine(pivotX, pivotY, endX, endY, paint)
        }
    }
}