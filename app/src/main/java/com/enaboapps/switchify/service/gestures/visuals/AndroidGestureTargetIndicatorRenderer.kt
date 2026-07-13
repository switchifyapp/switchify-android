package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

class AndroidGestureTargetIndicatorRenderer(context: Context) : GestureTargetIndicatorRenderer {
    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val accessibilityWindow = SwitchifyAccessibilityWindow.instance
    private val tokens = GestureVisualTokens(context)
    private var indicatorView: RelativeLayout? = null

    override fun show(point: GestureTargetPoint) {
        onMainThread {
            hideNow()
            val view = GestureCircleViewFactory.createTarget(context)
            indicatorView = view
            accessibilityWindow.addView(
                view,
                point.x - tokens.targetHalo / 2,
                point.y - tokens.targetHalo / 2,
                tokens.targetHalo,
                tokens.targetHalo
            )
            if (GestureVisualMotionPolicy.animationsEnabled()) {
                view.alpha = 0f
                view.scaleX = 0.72f
                view.scaleY = 0.72f
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .start()
            }
        }
    }

    override fun hide() {
        onMainThread(::hideNow)
    }

    override fun release() {
        onMainThread(::hideNow)
    }

    private fun hideNow() {
        indicatorView?.let(accessibilityWindow::removeView)
        indicatorView = null
    }

    private fun onMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

}
