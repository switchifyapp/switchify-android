package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import java.lang.ref.WeakReference

class ZoomVisual(context: Context) {
    private val contextRef: WeakReference<Context> = WeakReference(context)
    private var currentCircle: WeakReference<RelativeLayout>? = null
    private var currentAnimation: ScaleAnimation? = null
    private var removeHandler: Handler? = null

    fun start(
        x: Float,
        y: Float,
        circumference: Float,
        time: Long,
        isZoomIn: Boolean = true
    ) {
        stop()

        val circleLayout = createView(circumference)

        SwitchifyAccessibilityWindow.instance.addView(
            circleLayout,
            x.toInt() - (circumference / 2).toInt(),
            y.toInt() - (circumference / 2).toInt(),
            circumference.toInt(),
            circumference.toInt()
        )

        currentCircle = WeakReference(circleLayout)

        val scaleAnimation = ScaleAnimation(
            if (isZoomIn) 1f else 2f,
            if (isZoomIn) 2f else 1f,
            if (isZoomIn) 1f else 2f,
            if (isZoomIn) 2f else 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = time
            fillAfter = true
        }

        currentAnimation = scaleAnimation
        circleLayout.startAnimation(scaleAnimation)

        removeHandler = Handler(Looper.getMainLooper()).apply {
            postDelayed({
                removeView()
            }, time)
        }
    }

    private fun createView(circumference: Float): RelativeLayout {
        val context = contextRef.get() ?: throw IllegalStateException("Context is null")

        // Create shadow circle for depth
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(12, 0x30000000) // Semi-transparent black shadow
        }

        // Create main white circle
        val mainDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(10, 0xFFFFFFFF.toInt()) // Pure white stroke
        }

        // Shadow circle (slightly offset)
        val shadowView = ImageView(context).apply {
            setImageDrawable(shadowDrawable)
            layoutParams = RelativeLayout.LayoutParams(
                circumference.toInt(),
                circumference.toInt()
            ).apply {
                leftMargin = 3
                topMargin = 3
            }
        }

        // Main white circle
        val mainView = ImageView(context).apply {
            setImageDrawable(mainDrawable)
            layoutParams = RelativeLayout.LayoutParams(
                circumference.toInt(),
                circumference.toInt()
            )
        }

        return RelativeLayout(context).apply {
            addView(shadowView)
            addView(mainView)
        }
    }

    fun stop() {
        currentAnimation?.cancel()
        removeHandler?.removeCallbacksAndMessages(null)
        removeView()
    }

    private fun removeView() {
        currentCircle?.get()?.let {
            SwitchifyAccessibilityWindow.instance.removeView(it)
        }
        currentCircle = null
        currentAnimation = null
        removeHandler = null
    }
}