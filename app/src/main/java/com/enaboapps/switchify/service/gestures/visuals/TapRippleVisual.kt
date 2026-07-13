package com.enaboapps.switchify.service.gestures.visuals

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

class TapRippleVisual(private val context: Context) {
    private var container: RelativeLayout? = null
    private var animator: ValueAnimator? = null
    private var pendingRemoval: Pair<View, Runnable>? = null

    fun show(x: Int, y: Int, durationMs: Long = DEFAULT_DURATION_MS) {
        cancel()
        val tokens = GestureVisualTokens(context)
        val visual = TapLandingView(context).apply {
            layoutParams = RelativeLayout.LayoutParams(tokens.tapHalo, tokens.tapHalo)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val wrapper = RelativeLayout(context).apply { addView(visual) }
        container = wrapper
        SwitchifyAccessibilityWindow.instance.addView(
            wrapper,
            x - tokens.tapHalo / 2,
            y - tokens.tapHalo / 2,
            tokens.tapHalo,
            tokens.tapHalo
        )
        if (!GestureVisualMotionPolicy.animationsEnabled()) {
            visual.progress = STATIC_PROGRESS
            val removal = Runnable { remove(wrapper, null) }
            pendingRemoval = visual to removal
            visual.postDelayed(removal, durationMs)
            return
        }
        val animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
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

    private class TapLandingView(context: Context) : View(context) {
        private val tokens = GestureVisualTokens(context)
        var progress = 0f
            set(value) {
                field = value
                invalidate()
            }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = tokens.dp(3f).toFloat()
        }
        private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 0, 0, 0)
        }
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tokens.primary }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val phase = (progress / 0.72f).coerceAtMost(1f)
            val coreScale = (progress / 0.22f).coerceAtMost(1f)
            val fade = if (progress < 0.72f) 1f else 1f - (progress - 0.72f) / 0.28f
            halo.color = withAlpha(tokens.primary, (180 * fade).toInt())
            val maxHaloRadius = (width - halo.strokeWidth) / 2f
            val haloRadius = tokens.tapCore / 2f +
                (maxHaloRadius - tokens.tapCore / 2f) * phase
            canvas.drawCircle(cx, cy, haloRadius, halo)
            val coreRadius = tokens.tapCore / 2f * coreScale
            shadow.alpha = (65 * fade).toInt()
            core.alpha = (255 * fade).toInt()
            canvas.drawCircle(
                cx + tokens.shadowOffset,
                cy + tokens.shadowOffset,
                coreRadius,
                shadow
            )
            canvas.drawCircle(cx, cy, coreRadius, core)
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
        }
    }

    private companion object {
        const val DEFAULT_DURATION_MS = 300L
        const val STATIC_PROGRESS = 0.5f
    }
}
