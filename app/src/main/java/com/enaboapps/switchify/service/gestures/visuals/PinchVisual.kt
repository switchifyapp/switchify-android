package com.enaboapps.switchify.service.gestures.visuals

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.gestures.execution.GestureTouchPath
import com.enaboapps.switchify.service.gestures.execution.GesturePathMath
import com.enaboapps.switchify.service.gestures.execution.PinchGestureGeometry
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

class PinchVisual(private val context: Context) {
    private var container: RelativeLayout? = null
    private var animator: ValueAnimator? = null
    private var pendingRemoval: Pair<View, Runnable>? = null

    fun start(geometry: PinchGestureGeometry, time: Long) {
        stop()
        val visual = PinchOverlayView(context, geometry).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val wrapper = RelativeLayout(context).apply {
            addView(
                visual,
                RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        container = wrapper
        SwitchifyAccessibilityWindow.instance.addView(
            wrapper,
            0,
            0,
            ScreenUtils.getWidth(context),
            ScreenUtils.getHeight(context)
        )
        if (!GestureVisualMotionPolicy.animationsEnabled()) {
            visual.progress = 1f
            val removal = Runnable { remove(wrapper, null) }
            pendingRemoval = visual to removal
            visual.postDelayed(removal, STATIC_DWELL_MS)
            return
        }
        val animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = time
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { visual.progress = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    remove(wrapper, animation as ValueAnimator)
                }
            })
        }
        animator = animation
        animation.start()
    }

    fun stop() {
        pendingRemoval?.let { (view, runnable) -> view.removeCallbacks(runnable) }
        pendingRemoval = null
        val activeContainer = container
        val activeAnimator = animator
        activeAnimator?.removeAllListeners()
        activeAnimator?.cancel()
        if (activeContainer != null) remove(activeContainer, activeAnimator)
    }

    private fun remove(ownedContainer: RelativeLayout, ownedAnimator: ValueAnimator?) {
        SwitchifyAccessibilityWindow.instance.removeView(ownedContainer)
        if (container === ownedContainer) container = null
        if (animator === ownedAnimator) animator = null
        pendingRemoval = null
    }

    private class PinchOverlayView(
        context: Context,
        private val geometry: PinchGestureGeometry
    ) : View(context) {
        private val tokens = GestureVisualTokens(context)
        var progress: Float = 0f
            set(value) {
                field = value
                invalidate()
            }
        private val underlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 0, 0)
            strokeWidth = tokens.pathUnderlay
            strokeCap = Paint.Cap.ROUND
        }
        private val connector = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (tokens.primary and 0x00FFFFFF) or (110 shl 24)
            strokeWidth = tokens.pathStroke
            strokeCap = Paint.Cap.ROUND
        }
        private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tokens.primary }
        private val pointShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 0, 0)
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.onPrimary
            textAlign = Paint.Align.CENTER
            textSize = tokens.labelText
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        override fun onDraw(canvas: Canvas) {
            val first = interpolate(geometry.first)
            val second = interpolate(geometry.second)
            canvas.drawLine(
                geometry.first.start.x,
                geometry.first.start.y,
                geometry.first.end.x,
                geometry.first.end.y,
                underlay
            )
            canvas.drawLine(
                geometry.second.start.x,
                geometry.second.start.y,
                geometry.second.end.x,
                geometry.second.end.y,
                underlay
            )
            canvas.drawLine(first.first, first.second, second.first, second.second, connector)
            drawPoint(canvas, first.first, first.second, 1)
            drawPoint(canvas, second.first, second.second, 2)
        }

        private fun interpolate(path: GestureTouchPath): Pair<Float, Float> {
            val point = GesturePathMath.interpolate(path.start, path.end, progress)
            return Pair(point.x, point.y)
        }

        private fun drawPoint(canvas: Canvas, x: Float, y: Float, number: Int) {
            val radius = tokens.touchPoint / 2f
            canvas.drawCircle(
                x + tokens.shadowOffset,
                y + tokens.shadowOffset,
                radius,
                pointShadow
            )
            canvas.drawCircle(x, y, radius, point)
            val metrics = label.fontMetrics
            canvas.drawText(number.toString(), x, y - (metrics.ascent + metrics.descent) / 2f, label)
        }
    }

    private companion object {
        const val STATIC_DWELL_MS = 280L
    }
}
