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
    private var indicatorView: RelativeLayout? = null

    override fun show(point: GestureTargetPoint) {
        onMainThread {
            hideNow()
            val view = GestureCircleViewFactory.create(context, INDICATOR_SIZE)
            indicatorView = view
            accessibilityWindow.addView(
                view,
                point.x - INDICATOR_SIZE / 2,
                point.y - INDICATOR_SIZE / 2,
                INDICATOR_SIZE,
                INDICATOR_SIZE
            )
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

    private companion object {
        const val INDICATOR_SIZE = 48
    }
}
