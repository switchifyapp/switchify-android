package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

/**
 * One-shot filled dot that pops in, holds, and fades — the "tap landed here"
 * anchor for single-finger tap and double-tap gestures.
 *
 * Matches the inner dot of [TapAndHoldRingVisual] in diameter and colour so a
 * plain tap and a tap-and-hold read as the same family — a tap-and-hold is
 * just a tap with a ring overlay around it.
 *
 * The 280 ms animation has three perceptible phases:
 *  - 0–60 ms: pop in (scale 0.7× → 1.0× with a small overshoot).
 *  - 60–200 ms: hold at full size, full alpha. This is the perceptual window
 *    the eye needs to lock on; with the previous 150 ms fading-ripple the dot
 *    was gone before users with reduced visual acuity could register it.
 *  - 200–280 ms: alpha 1.0 → 0. The dot dissolves in place — no extra scaling,
 *    so the anchor stays put as it disappears.
 *
 * Two dots in quick succession (via [GestureVisualManager.showDoubleTapRipple],
 * staggered 250 ms apart) are how a double tap is read as two distinct events
 * rather than one emphatic flash.
 */
class TapRippleVisual(private val context: Context) {
    private var container: RelativeLayout? = null
    private var currentAnimation: Animation? = null

    /**
     * Show the dot centred on ([x], [y]) and auto-remove when the animation
     * completes. Cancels any in-flight dot from this instance first.
     */
    fun show(x: Int, y: Int, durationMs: Long = DEFAULT_DURATION_MS) {
        cancel()

        val dot = ImageView(context).apply {
            setImageDrawable(buildDotDrawable())
            layoutParams = RelativeLayout.LayoutParams(DOT_SIZE_PX, DOT_SIZE_PX)
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
            addView(dot)
        }

        SwitchifyAccessibilityWindow.instance.addView(
            wrapper,
            x - DOT_SIZE_PX / 2,
            y - DOT_SIZE_PX / 2,
            DOT_SIZE_PX,
            DOT_SIZE_PX
        )
        container = wrapper

        // shareInterpolator = false so each child animation keeps its own
        // interpolator and startOffset. The pop-in uses an OvershootInterpolator
        // for a tiny "land" pop; the fade-out runs linearly after a hold.
        val animation = AnimationSet(false).apply {
            addAnimation(
                ScaleAnimation(
                    POP_START_SCALE, 1f,
                    POP_START_SCALE, 1f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = POP_IN_MS
                    interpolator = OvershootInterpolator(POP_OVERSHOOT_TENSION)
                }
            )
            addAnimation(
                AlphaAnimation(1f, 0f).apply {
                    startOffset = FADE_START_MS
                    this.duration = FADE_DURATION_MS
                }
            )
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
        dot.startAnimation(animation)
    }

    /** Cancel any in-flight dot from this instance and remove its view. */
    fun cancel() {
        currentAnimation?.cancel()
        removeView()
    }

    private fun removeView() {
        container?.let { SwitchifyAccessibilityWindow.instance.removeView(it) }
        container = null
        currentAnimation = null
    }

    private fun buildDotDrawable(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        // Solid fill in the brand primary — the colour resource resolves to
        // the light/dark variant automatically.
        setColor(ContextCompat.getColor(context, R.color.gesture_visual_primary))
        setSize(DOT_SIZE_PX, DOT_SIZE_PX)
    }

    companion object {
        // 280 ms total: 60 ms pop in, 140 ms hold, 80 ms fade. Long enough for
        // users with slower visual processing to lock on; short enough that
        // tapping doesn't feel laggy.
        private const val DEFAULT_DURATION_MS = 280L
        private const val POP_IN_MS = 60L
        private const val FADE_START_MS = 200L
        private const val FADE_DURATION_MS = 80L

        // A plain tap stands alone (no ring around it), so it can sit a bit
        // larger than the 44 px inner dot of TapAndHoldRingVisual without
        // looking out of family — both are still solid filled circles in
        // the same primary colour, just a tap-and-hold's anchor is sized
        // smaller so the ring around it has room to breathe.
        private const val DOT_SIZE_PX = 56

        private const val POP_START_SCALE = 0.7f
        private const val POP_OVERSHOOT_TENSION = 1.5f
    }
}
