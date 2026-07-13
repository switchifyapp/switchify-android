package com.enaboapps.switchify.service.gestures.visuals

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PointF
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

class AnimatedGestureArrow(
    private val context: Context,
    private val fingerLabel: Int? = null
) {
    private var container: RelativeLayout? = null
    private var animator: ValueAnimator? = null
    private var pendingRemoval: Pair<View, Runnable>? = null

    fun showArrowAnimation(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long,
        onAnimationEnd: () -> Unit = {}
    ) {
        cancel()
        val pathView = GesturePathOverlayView(
            context,
            PointF(startX.toFloat(), startY.toFloat()),
            PointF(endX.toFloat(), endY.toFloat()),
            fingerLabel
        ).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val wrapper = RelativeLayout(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(pathView)
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
            pathView.progress = 1f
            val removal = Runnable { remove(wrapper, null, onAnimationEnd) }
            pendingRemoval = pathView to removal
            pathView.postDelayed(removal, STATIC_DWELL_MS)
            return
        }

        val animation = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { pathView.progress = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    remove(wrapper, animation as ValueAnimator, onAnimationEnd)
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
        if (activeContainer != null) remove(activeContainer, activeAnimator) {}
    }

    private fun remove(
        ownedContainer: RelativeLayout,
        ownedAnimator: ValueAnimator?,
        onRemoved: () -> Unit
    ) {
        SwitchifyAccessibilityWindow.instance.removeView(ownedContainer)
        if (container === ownedContainer) container = null
        if (animator === ownedAnimator) animator = null
        pendingRemoval = null
        onRemoved()
    }

    private companion object {
        const val STATIC_DWELL_MS = 280L
    }
}
