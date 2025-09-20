package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.gestures.GestureStateManager
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import java.lang.ref.WeakReference

/**
 * Unified visual feedback manager for all gesture-related visual indicators.
 * Consolidates and standardizes visual feedback across the gesture system.
 * Integrates with GestureStateManager for coordinated state management.
 */
class GestureVisualManager(context: Context) : GestureStateManager.GestureStateListener {

    private val contextRef: WeakReference<Context> = WeakReference(context)
    private val accessibilityWindow = SwitchifyAccessibilityWindow.instance
    private val animatedGestureArrow = AnimatedGestureArrow(context)

    // Active visual tracking
    private var currentCircle: WeakReference<RelativeLayout>? = null
    private var currentAnimation: ScaleAnimation? = null
    private var removeHandler: Handler? = null

    companion object {
        // Standardized circle size - compromise between existing 40px and 60px
        private const val STANDARD_CIRCLE_SIZE = 48
    }

    init {
        // Register as state listener for coordinated visual feedback
        GestureStateManager.addStateListener("visual_manager", this)
    }

    /**
     * Shows a static circle at the specified coordinates.
     * @param x The x-coordinate of the circle's center
     * @param y The y-coordinate of the circle's center
     * @param duration Duration in milliseconds, null for persistent circle
     */
    fun showStaticCircle(x: Int, y: Int, duration: Long? = null) {
        clearCurrentVisual()

        val context = contextRef.get() ?: return
        val circleLayout = createCircleLayout(context, STANDARD_CIRCLE_SIZE)

        accessibilityWindow.addView(
            circleLayout,
            x - STANDARD_CIRCLE_SIZE / 2,
            y - STANDARD_CIRCLE_SIZE / 2,
            STANDARD_CIRCLE_SIZE,
            STANDARD_CIRCLE_SIZE
        )

        currentCircle = WeakReference(circleLayout)

        // Auto-remove after duration if specified
        duration?.let {
            removeHandler = Handler(Looper.getMainLooper()).apply {
                postDelayed({ clearCurrentVisual() }, it)
            }
        }
    }

    /**
     * Shows a countdown circle with shrinking animation.
     * @param x The x-coordinate of the circle's center
     * @param y The y-coordinate of the circle's center
     * @param duration Duration of the countdown animation in milliseconds
     */
    fun showCountdownCircle(x: Int, y: Int, duration: Long) {
        clearCurrentVisual()

        val context = contextRef.get() ?: return
        val circleLayout = createCircleLayout(context, STANDARD_CIRCLE_SIZE)

        accessibilityWindow.addView(
            circleLayout,
            x - STANDARD_CIRCLE_SIZE / 2,
            y - STANDARD_CIRCLE_SIZE / 2,
            STANDARD_CIRCLE_SIZE,
            STANDARD_CIRCLE_SIZE
        )

        currentCircle = WeakReference(circleLayout)

        // Create shrinking animation
        val scaleAnimation = ScaleAnimation(
            1f, 0f, 1f, 0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration
            fillAfter = true
        }

        currentAnimation = scaleAnimation
        // Apply animation to both shadow and main circle views for proper countdown effect
        circleLayout.getChildAt(0)?.startAnimation(scaleAnimation) // shadowView
        circleLayout.getChildAt(1)?.startAnimation(scaleAnimation) // mainView

        // Auto-remove after animation
        removeHandler = Handler(Looper.getMainLooper()).apply {
            postDelayed({ clearCurrentVisual() }, duration)
        }
    }

    /**
     * Shows an animated arrow from start to end point.
     * @param x1 Start x-coordinate
     * @param y1 Start y-coordinate
     * @param x2 End x-coordinate
     * @param y2 End y-coordinate
     * @param duration Animation duration in milliseconds
     */
    fun showArrowAnimation(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long) {
        animatedGestureArrow.showArrowAnimation(x1, y1, x2, y2, duration)
    }

    /**
     * Hides any currently displayed circle visual.
     */
    fun hideCircle() {
        clearCurrentVisual()
    }

    /**
     * Creates a standardized circle layout with modern white styling.
     */
    private fun createCircleLayout(context: Context, size: Int): RelativeLayout {
        // Create shadow circle (slightly offset and darker)
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x20000000) // Semi-transparent black shadow
            setSize(size, size)
        }

        // Create main white circle
        val mainDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFFFFFFF.toInt()) // Pure white
            setStroke(1, 0x20000000) // Subtle dark stroke for definition
            setSize(size, size)
        }

        // Shadow layer
        val shadowView = ImageView(context).apply {
            setImageDrawable(shadowDrawable)
            layoutParams = RelativeLayout.LayoutParams(size, size).apply {
                leftMargin = 2
                topMargin = 2
            }
        }

        // Main circle layer
        val mainView = ImageView(context).apply {
            setImageDrawable(mainDrawable)
            layoutParams = RelativeLayout.LayoutParams(size, size)
        }

        return RelativeLayout(context).apply {
            addView(shadowView)
            addView(mainView)
        }
    }

    /**
     * Clears any current visual and associated handlers/animations.
     */
    private fun clearCurrentVisual() {
        currentAnimation?.cancel()
        removeHandler?.removeCallbacksAndMessages(null)

        currentCircle?.get()?.let { circle ->
            accessibilityWindow.removeView(circle)
        }

        currentCircle = null
        currentAnimation = null
        removeHandler = null
    }

    /**
     * Implements GestureStateListener to coordinate visual feedback with state changes.
     */
    override fun onStateChanged(event: String, data: Map<String, Any>) {
        when (event) {
            GestureStateManager.EVENT_GESTURE_ENDED -> {
                // Auto-hide visual feedback when gesture ends
                if (data["cancelled"] == true) {
                    hideCircle()
                }
            }

            GestureStateManager.EVENT_AUTO_SELECT_CANCELLED -> {
                // Hide countdown visual when auto-select is cancelled
                hideCircle()
            }

            GestureStateManager.EVENT_STATE_RESET -> {
                // Clear all visuals on state reset
                clearCurrentVisual()
            }
        }
    }

    /**
     * Releases all resources and clears references.
     */
    fun release() {
        GestureStateManager.removeStateListener("visual_manager")
        clearCurrentVisual()
        contextRef.clear()
    }
}