package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.PorterDuff
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import kotlin.math.atan2

class AnimatedGestureArrow(private val context: Context) {
    private var arrowView: ImageView? = null
    private var arrowContainer: RelativeLayout? = null
    private var currentAnimation: Animation? = null

    fun showArrowAnimation(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long,
        onAnimationEnd: () -> Unit = {}
    ) {
        // Cancel any existing animation before starting a new one
        cancel()

        val size = 150
        val halfSize = size / 2
        // Create arrow ImageView
        val arrow = ImageView(context).apply {
            setImageResource(R.drawable.gesture_arrow)
            // Apply the secondary scanning color
            setColorFilter(
                ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor.toColorInt(),
                PorterDuff.Mode.SRC_IN
            )
            // Set the size of the arrow (adjust as needed)
            layoutParams = RelativeLayout.LayoutParams(size, size).apply {
                // Position the arrow at the start coordinates within the full-screen container
                leftMargin = startX - halfSize // Center the arrow horizontally
                topMargin = startY - halfSize  // Center the arrow vertically
            }
        }

        // Calculate rotation angle - adjusted to point in direction of movement
        val angle = Math.toDegrees(atan2((endY - startY).toDouble(), (endX - startX).toDouble()))
        arrow.rotation = angle.toFloat() + 90 // +90 to align with movement direction

        // Create container for the arrow that fills the screen
        val container = RelativeLayout(context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            addView(arrow)
        }

        // Add to window at (0,0) with screen dimensions
        SwitchifyAccessibilityWindow.instance.addView(
            container,
            0,
            0,
            ScreenUtils.getWidth(context),
            ScreenUtils.getHeight(context)
        )

        // Store references
        arrowView = arrow
        arrowContainer = container

        // Create and start animation
        val animation = TranslateAnimation(
            0f, (endX - startX).toFloat(),
            0f, (endY - startY).toFloat()
        ).apply {
            this.duration = duration
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    removeArrow()
                    onAnimationEnd()
                }
            })
        }

        currentAnimation = animation
        arrow.startAnimation(animation)
    }

    private fun removeArrow() {
        arrowContainer?.let {
            SwitchifyAccessibilityWindow.instance.removeView(it)
        }
        arrowView = null
        arrowContainer = null
        currentAnimation = null
    }

    fun cancel() {
        currentAnimation?.cancel()
        removeArrow()
    }
} 