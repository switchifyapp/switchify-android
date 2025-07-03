package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import androidx.core.graphics.toColorInt
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

/**
 * GestureDrawing class is responsible for drawing visual feedback for user interactions
 * using the SwitchifyAccessibilityWindow.
 *
 * @property context The context used to create views and access resources.
 */
class GestureDrawing(private val context: Context) {

    // Instance of SwitchifyAccessibilityWindow used to manage views
    private val switchifyAccessibilityWindow = SwitchifyAccessibilityWindow.instance
    private val animatedGestureArrow = AnimatedGestureArrow(context)

    /**
     * Draws a circle at the specified coordinates and removes it after a given time.
     *
     * @param x The x-coordinate of the circle's center.
     * @param y The y-coordinate of the circle's center.
     * @param time The duration in milliseconds for which the circle should be visible.
     */
    fun drawCircleAndRemove(
        x: Int,
        y: Int,
        time: Long,
    ) {
        val circleSize = 40

        // Create a circular drawable for the circle
        val gradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor.toColorInt()
            )
            setSize(circleSize, circleSize)
        }

        // Create an ImageView to display the circle
        val circle = ImageView(context).apply {
            setImageDrawable(gradientDrawable)
        }

        // Wrap the ImageView in a RelativeLayout for easier positioning
        val circleLayout = RelativeLayout(context).apply {
            addView(circle, RelativeLayout.LayoutParams(circleSize, circleSize))
        }

        // Add the circle to the accessibility window
        switchifyAccessibilityWindow.addView(
            circleLayout,
            x - circleSize / 2,
            y - circleSize / 2,
            circleSize,
            circleSize
        )

        // Remove the circle after the specified time
        Handler(Looper.getMainLooper()).postDelayed({
            switchifyAccessibilityWindow.removeView(circleLayout)
        }, time)
    }

    /**
     * Shows an animated arrow from (x1, y1) to (x2, y2) and removes it after a given time.
     *
     * @param x1 The x-coordinate of the start point.
     * @param y1 The y-coordinate of the start point.
     * @param x2 The x-coordinate of the end point.
     * @param y2 The y-coordinate of the end point.
     * @param time The duration in milliseconds for which the arrow should be visible.
     */
    fun showAnimatedArrow(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        time: Long,
    ) {
        animatedGestureArrow.showArrowAnimation(x1, y1, x2, y2, time)
    }
}