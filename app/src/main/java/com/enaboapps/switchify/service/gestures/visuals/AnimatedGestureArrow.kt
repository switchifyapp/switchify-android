package com.enaboapps.switchify.service.gestures.visuals

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

class AnimatedGestureArrow(
    private val context: Context,
    private val fingerLabel: Int? = null
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeSession: VisualSession? = null

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
        val session = VisualSession(wrapper, onAnimationEnd)
        session.cleanupCoordinator = GestureVisualCleanupCoordinator(
            postDelayed = { runnable, delayMs -> mainHandler.postDelayed(runnable, delayMs) },
            removeCallbacks = mainHandler::removeCallbacks,
            cleanup = { finish(session) }
        )
        activeSession = session
        SwitchifyAccessibilityWindow.instance.addView(
            wrapper,
            0,
            0,
            ScreenUtils.getWidth(context),
            ScreenUtils.getHeight(context)
        )

        if (!GestureVisualMotionPolicy.animationsEnabled()) {
            pathView.progress = 1f
            session.cleanupCoordinator.schedule(STATIC_DWELL_MS)
            return
        }

        val animation = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { pathView.progress = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    session.cleanupCoordinator.complete()
                }

                override fun onAnimationEnd(animation: Animator) {
                    session.cleanupCoordinator.complete()
                }
            })
        }
        session.animator = animation
        session.cleanupCoordinator.schedule(
            GestureVisualCleanupDeadline.calculate(
                durationMs = duration,
                durationScale = ValueAnimator.getDurationScale(),
                graceMs = CLEANUP_GRACE_MS
            )
        )
        animation.start()
    }

    fun cancel() {
        activeSession?.cleanupCoordinator?.complete()
    }

    private fun finish(session: VisualSession) {
        session.animator?.let { animation ->
            animation.removeAllListeners()
            if (animation.isStarted) animation.cancel()
        }
        SwitchifyAccessibilityWindow.instance.removeView(session.container)
        if (activeSession === session) activeSession = null
        session.onRemoved()
    }

    private class VisualSession(
        val container: RelativeLayout,
        val onRemoved: () -> Unit
    ) {
        lateinit var cleanupCoordinator: GestureVisualCleanupCoordinator
        var animator: ValueAnimator? = null
    }

    private companion object {
        const val STATIC_DWELL_MS = 280L
        const val CLEANUP_GRACE_MS = 250L
    }
}
