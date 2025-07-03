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

class AutoTapVisual(context: Context) {

    private val contextRef: WeakReference<Context> = WeakReference(context)
    private var currentCircle: WeakReference<RelativeLayout>? = null
    private var currentAnimation: ScaleAnimation? = null
    private var removeHandler: Handler? = null

    fun start(
        x: Float,
        y: Float,
        time: Long,
        initialSize: Int = 60
    ) {
        // Stop any existing animation
        stop()

        val context = contextRef.get() ?: return

        val gradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor.toColorInt()
            )
            setSize(initialSize, initialSize)
        }

        val imageView = ImageView(context).apply {
            setImageDrawable(gradientDrawable)
        }

        val circleLayout = RelativeLayout(context).apply {
            addView(imageView, RelativeLayout.LayoutParams(initialSize, initialSize))
        }

        SwitchifyAccessibilityWindow.instance.addView(
            circleLayout,
            x.toInt() - initialSize / 2,
            y.toInt() - initialSize / 2,
            initialSize,
            initialSize
        )

        currentCircle = WeakReference(circleLayout)

        val scaleAnimation = ScaleAnimation(
            1f, 0f, 1f, 0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = time
            fillAfter = true
        }

        currentAnimation = scaleAnimation
        imageView.startAnimation(scaleAnimation)

        removeHandler = Handler(Looper.getMainLooper()).apply {
            postDelayed({
                removeView()
            }, time)
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

    fun release() {
        stop()
        contextRef.clear()
    }
}