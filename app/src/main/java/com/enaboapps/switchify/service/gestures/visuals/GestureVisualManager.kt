package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import androidx.core.graphics.toColorInt
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import java.lang.ref.WeakReference

/**
 * Unified visual feedback manager for all gesture-related visual indicators.
 * Consolidates and standardizes visual feedback across the gesture system.
 */
class GestureVisualManager(context: Context) {
    
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
        circleLayout.getChildAt(0)?.startAnimation(scaleAnimation)
        
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
     * Creates a standardized circle layout with unified styling.
     */
    private fun createCircleLayout(context: Context, size: Int): RelativeLayout {
        val gradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor.toColorInt()
            )
            setSize(size, size)
        }
        
        val imageView = ImageView(context).apply {
            setImageDrawable(gradientDrawable)
        }
        
        return RelativeLayout(context).apply {
            addView(imageView, RelativeLayout.LayoutParams(size, size))
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
     * Releases all resources and clears references.
     */
    fun release() {
        clearCurrentVisual()
        contextRef.clear()
    }
}