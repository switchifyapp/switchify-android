package com.enaboapps.switchify.service.gestures.visuals

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

/**
 * On-screen feedback for a single-finger tap-and-hold gesture.
 *
 * Renders three layers around the gesture point:
 *   1. A solid white inner dot.
 *   2. A thin outline ring that fills clockwise (0° → 360°) over the hold
 *      duration. When the ring completes, the gesture has just fired.
 *   3. The duration value as text inside the dot ("0.5s", "1s", "2s"…), so the
 *      on-screen visual matches the menu item the user selected.
 *
 * Together these convey *kind* (it's a hold, not a tap), *progress* (how
 * close to firing), and *duration* (which hold variant) at a glance.
 */
class TapAndHoldRingVisual(private val context: Context) {
    private var container: RelativeLayout? = null
    private var animator: ValueAnimator? = null

    /**
     * Show the visual centred on ([x], [y]) and animate the ring fill over
     * [durationMs]. Cancels any in-flight visual from this instance first.
     */
    fun show(x: Int, y: Int, durationMs: Long) {
        cancel()

        val primary = ContextCompat.getColor(context, R.color.gesture_visual_primary)
        val onPrimary = ContextCompat.getColor(context, R.color.gesture_visual_on_primary)
        val ringView = RingView(context, durationLabel(durationMs), primary, onPrimary).apply {
            layoutParams = RelativeLayout.LayoutParams(CONTAINER_SIZE_PX, CONTAINER_SIZE_PX)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // SwitchifyAccessibilityWindow.addView requires a ViewGroup, so wrap
        // the custom RingView in a RelativeLayout — same pattern as the
        // other helpers in this directory.
        val wrapper = RelativeLayout(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(ringView)
        }

        SwitchifyAccessibilityWindow.instance.addView(
            wrapper,
            x - CONTAINER_SIZE_PX / 2,
            y - CONTAINER_SIZE_PX / 2,
            CONTAINER_SIZE_PX,
            CONTAINER_SIZE_PX
        )
        container = wrapper

        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = durationMs
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                ringView.sweepAngle = progress
                ringView.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView()
                }
            })
            start()
        }
    }

    /** Cancel any in-flight ring animation and remove the view. */
    fun cancel() {
        animator?.cancel()
        removeView()
    }

    private fun removeView() {
        container?.let { SwitchifyAccessibilityWindow.instance.removeView(it) }
        container = null
        animator = null
    }

    /**
     * Format a millisecond hold duration as the human-readable label that
     * appears inside the dot. Whole seconds drop the decimal ("1s"), sub-second
     * holds use one decimal place ("0.5s").
     */
    private fun durationLabel(ms: Long): String =
        if (ms % 1000L == 0L) "${ms / 1000}s" else "${ms / 1000.0}s"

    /**
     * Custom view drawing: shadow ring + dot + progress arc + duration text.
     * [sweepAngle] is updated by the animator and `invalidate()` triggers the
     * redraw.
     *
     * Colours follow Direction B — filled primary: the inner dot is the
     * brand primary colour, the duration label is the on-primary contrast
     * colour, and the ring track / progress arc are tints of the same
     * primary so the whole visual reads as a single brand-coloured object.
     */
    private class RingView(
        context: Context,
        private val label: String,
        primaryColor: Int,
        onPrimaryColor: Int
    ) : View(context) {
        var sweepAngle: Float = 0f

        private val shadowRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (RING_STROKE_PX + 2).toFloat()
            color = Color.argb(60, 0, 0, 0)
        }

        private val ringTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE_PX.toFloat()
            // Faint primary track so the user sees the ring outline before
            // the progress arc reaches that point.
            color = withAlpha(primaryColor, 80)
        }

        private val ringProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE_PX.toFloat()
            color = primaryColor
            strokeCap = Paint.Cap.ROUND
        }

        private val dotShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
        }

        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
        }

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onPrimaryColor
            textAlign = Paint.Align.CENTER
            textSize = LABEL_TEXT_SIZE_PX.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)

        private val arcRect = RectF()

        override fun onDraw(canvas: Canvas) {
            val cx = (width / 2f)
            val cy = (height / 2f)

            // Ring geometry: stroke is centred on the path, so inset by half
            // the stroke width to keep it inside the view bounds.
            val ringRadius = (CONTAINER_SIZE_PX - RING_STROKE_PX) / 2f
            arcRect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)

            // Subtle drop shadow on the ring track for visibility on light
            // backgrounds.
            canvas.drawArc(arcRect, 0f, 360f, false, shadowRingPaint)
            // Faint full-circle track so the user sees the ring outline even
            // when the progress arc is short.
            canvas.drawArc(arcRect, 0f, 360f, false, ringTrackPaint)
            // Progress arc, starting at 12 o'clock (-90°) and sweeping
            // clockwise to the current animated angle.
            canvas.drawArc(arcRect, -90f, sweepAngle, false, ringProgressPaint)

            // Inner dot — drawn on top of (and inside) the ring. Slight
            // shadow offset for depth, matching the existing visuals'
            // shadow style.
            canvas.drawCircle(cx + 2f, cy + 2f, DOT_RADIUS_PX.toFloat(), dotShadowPaint)
            canvas.drawCircle(cx, cy, DOT_RADIUS_PX.toFloat(), dotPaint)

            // Centre the text vertically: shift baseline by half the font's
            // ascent + descent so the visual centre of the glyph sits at cy.
            val fm = labelPaint.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(label, cx, textY, labelPaint)
        }
    }

    companion object {
        // Container holds ring + dot. 96 px gives ~6 px of clearance on each
        // side after the 3 px ring stroke, comfortably bigger than the
        // existing 48 px STANDARD_CIRCLE_SIZE for taps so a hold is
        // visually distinct from a tap at a glance.
        private const val CONTAINER_SIZE_PX = 96
        private const val RING_STROKE_PX = 4
        private const val DOT_RADIUS_PX = 22  // ~44 px diameter, fits inside the ring with margin
        private const val LABEL_TEXT_SIZE_PX = 22
    }
}
