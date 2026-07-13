package com.enaboapps.switchify.service.gestures.visuals

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

internal class CountdownProgressVisual(private val context: Context) {
    private var container: RelativeLayout? = null
    private var animator: ValueAnimator? = null
    private var pendingRemoval: Pair<View, Runnable>? = null

    fun show(x: Int, y: Int, durationMs: Long) {
        cancel()
        val tokens = GestureVisualTokens(context)
        val visual = CountdownView(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                tokens.progressContainer,
                tokens.progressContainer
            )
        }
        val wrapper = RelativeLayout(context).apply { addView(visual) }
        container = wrapper
        SwitchifyAccessibilityWindow.instance.addView(
            wrapper,
            x - tokens.progressContainer / 2,
            y - tokens.progressContainer / 2,
            tokens.progressContainer,
            tokens.progressContainer
        )
        if (!GestureVisualMotionPolicy.animationsEnabled()) {
            val removal = Runnable { remove(wrapper, null) }
            pendingRemoval = visual to removal
            visual.postDelayed(removal, durationMs)
            return
        }
        val animation = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = durationMs
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

    fun cancel() {
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

    private class CountdownView(context: Context) : View(context) {
        private val tokens = GestureVisualTokens(context)
        var progress = 1f
            set(value) {
                field = value
                invalidate()
            }
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 0, 0, 0)
        }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.progressStroke
            strokeCap = Paint.Cap.ROUND
            color = (tokens.primary and 0x00FFFFFF) or (70 shl 24)
        }
        private val progressPaint = Paint(track).apply { color = tokens.primary }
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tokens.primary }
        private val arc = RectF()

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = (width - tokens.progressStroke) / 2f
            arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arc, 0f, 360f, false, track)
            canvas.drawArc(arc, -90f, 360f * progress, false, progressPaint)
            val coreRadius = tokens.targetCore / 2f
            canvas.drawCircle(
                cx + tokens.shadowOffset,
                cy + tokens.shadowOffset,
                coreRadius,
                shadow
            )
            canvas.drawCircle(cx, cy, coreRadius, core)
        }
    }
}
