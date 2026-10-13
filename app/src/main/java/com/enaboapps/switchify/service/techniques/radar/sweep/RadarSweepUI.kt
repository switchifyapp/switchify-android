package com.enaboapps.switchify.service.techniques.radar.sweep

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.scanning.ScanVisualConstants
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.techniques.blocks.BlockOutline
import com.enaboapps.switchify.service.techniques.blocks.ScanBlock
import com.enaboapps.switchify.service.utils.ScreenUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the radar sweep line and the travelling circle. The sweep is confined to
 * [RadarSweepGeometry.bounds], which is the whole screen in radar-only mode or the
 * chosen block in grid + radar mode.
 */
class RadarSweepUI(private val context: Context) : AccessTechniqueUIBase() {
    private var radarLineContainer: FrameLayout? = null
    private var radarLine: RadarLineView? = null
    private var radarCircle: RelativeLayout? = null
    private val blockOutline = BlockOutline(context, this)

    companion object {
        const val RADAR_CIRCLE_SIZE = 50
    }

    private val screenWidth: Int
        get() = ScreenUtils.getWidth(context)

    private val screenHeight: Int
        get() = ScreenUtils.getHeight(context)

    fun showRadarLine(angle: Float, geometry: RadarSweepGeometry) {
        if (radarLineContainer == null) {
            createRadarLine()
        }
        radarLine?.update(angle, geometry)
    }

    fun showRadarCircle(x: Int, y: Int) {
        if (radarCircle == null) {
            createRadarCircle(x, y)
        } else {
            updateRadarCircle(x, y)
        }
    }

    fun setBlock(block: ScanBlock?) {
        blockOutline.show(block)
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
            alpha = (ScanVisualConstants.RADAR_ALPHA * 255).toInt()
        }
        radarCircle = RelativeLayout(context).apply {
            background = drawable
        }
        updateRadarCircle(x, y)
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
            blockOutline.remove()
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
        blockOutline.clear()
        super.hide()
    }

    private inner class RadarLineView(context: Context) : View(context) {
        private val paint = Paint().apply {
            strokeWidth =
                ScreenUtils.dpToPx(context, ScanVisualConstants.CURSOR_LINE_DP).toFloat()
            style = Paint.Style.STROKE
        }
        private var currentAngle = 0f
        private var pivotX = 0f
        private var pivotY = 0f
        private val bounds = Rect()

        fun setColor(color: Int) {
            paint.color = color
            paint.alpha = (ScanVisualConstants.RADAR_ALPHA * 255).toInt()
        }

        fun update(angle: Float, geometry: RadarSweepGeometry) {
            currentAngle = angle
            pivotX = geometry.pivotX
            pivotY = geometry.pivotY
            bounds.set(geometry.bounds)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (bounds.isEmpty) return
            val maxLength = sqrt(
                (bounds.width() * bounds.width() + bounds.height() * bounds.height()).toFloat()
            )
            val endX = pivotX + maxLength * cos(Math.toRadians(currentAngle.toDouble())).toFloat()
            val endY = pivotY + maxLength * sin(Math.toRadians(currentAngle.toDouble())).toFloat()
            canvas.save()
            canvas.clipRect(bounds)
            canvas.drawLine(pivotX, pivotY, endX, endY, paint)
            canvas.restore()
        }
    }
}
