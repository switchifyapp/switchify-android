package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

/**
 * One-shot expanding ripple at a screen point. The "tap fired here" affordance
 * for single-finger tap and double-tap gestures.
 *
 * The visual is an outline circle that scales 1× → 2× while alpha fades 1 → 0
 * over [DEFAULT_DURATION_MS]. Two ripples in quick succession (via
 * [GestureVisualManager.showDoubleTapRipple]) are how a double tap is read as
 * two distinct events instead of an indistinguishable flash.
 */
class TapRippleVisual(private val context: Context) {
    private var container: RelativeLayout? = null
    private var currentAnimation: Animation? = null

    /**
     * Show the ripple centred on ([x], [y]) and auto-remove when the animation
     * completes. Cancels any in-flight ripple from this instance first.
     */
    fun show(x: Int, y: Int, durationMs: Long = DEFAULT_DURATION_MS) {
        cancel()

        val ripple = ImageView(context).apply {
            setImageDrawable(buildRippleDrawable())
            layoutParams = RelativeLayout.LayoutParams(INITIAL_SIZE_PX, INITIAL_SIZE_PX)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // SwitchifyAccessibilityWindow.addView requires a ViewGroup, so wrap
        // the ImageView in a RelativeLayout — same pattern as
        // AnimatedGestureArrow and the static circles in
        // GestureVisualManager.createCircleLayout.
        val wrapper = RelativeLayout(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(ripple)
        }

        SwitchifyAccessibilityWindow.instance.addView(
            wrapper,
            x - INITIAL_SIZE_PX / 2,
            y - INITIAL_SIZE_PX / 2,
            INITIAL_SIZE_PX,
            INITIAL_SIZE_PX
        )
        container = wrapper

        val animation = AnimationSet(true).apply {
            addAnimation(
                ScaleAnimation(
                    1f, MAX_SCALE,
                    1f, MAX_SCALE,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                )
            )
            addAnimation(AlphaAnimation(1f, 0f))
            this.duration = durationMs
            fillAfter = false
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    removeView()
                }
            })
        }

        currentAnimation = animation
        ripple.startAnimation(animation)
    }

    /** Cancel any in-flight ripple from this instance and remove its view. */
    fun cancel() {
        currentAnimation?.cancel()
        removeView()
    }

    private fun removeView() {
        container?.let { SwitchifyAccessibilityWindow.instance.removeView(it) }
        container = null
        currentAnimation = null
    }

    private fun buildRippleDrawable(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        // Transparent fill, white outline — reads as a "ripple ring" once it scales.
        setColor(0x00000000)
        setStroke(STROKE_PX, 0xFFFFFFFF.toInt())
        setSize(INITIAL_SIZE_PX, INITIAL_SIZE_PX)
    }

    companion object {
        private const val DEFAULT_DURATION_MS = 150L

        // 32 px starting diameter so the 2× target lands ~64 px — comparable
        // visual weight to the existing 48 px STANDARD_CIRCLE_SIZE in
        // GestureVisualManager.
        private const val INITIAL_SIZE_PX = 32
        private const val MAX_SCALE = 2f
        private const val STROKE_PX = 3
    }
}
