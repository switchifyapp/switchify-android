package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.R

class HeadControlOverlay(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var overlayView: ImageView? = null
    private var isVisible = false

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun createOverlayView() {
        overlayView = ImageView(context).apply {
            setImageResource(R.drawable.ic_reselect) // Using existing icon
            setColorFilter(Color.CYAN) // Distinct color for head control
            scaleX = 1.2f
            scaleY = 1.2f
            alpha = 0.8f
        }
    }

    fun showPointer(x: Int, y: Int) {
        overlayView?.let { view ->
            if (!isVisible) {
                addToWindow(view)
                isVisible = true
            }
            updatePosition(view, x, y)
        }
    }

    fun hidePointer() {
        overlayView?.let { view ->
            if (isVisible) {
                try {
                    windowManager?.removeView(view)
                    isVisible = false
                } catch (e: Exception) {
                    // View might already be removed
                }
            }
        }
    }

    fun reset() {
        hidePointer()
    }

    private fun addToWindow(view: View) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            // Handle case where view is already added
        }
    }

    private fun updatePosition(view: View, x: Int, y: Int) {
        val params = view.layoutParams as? WindowManager.LayoutParams
        params?.let {
            it.x = x - view.width / 2
            it.y = y - view.height / 2
            try {
                windowManager?.updateViewLayout(view, it)
            } catch (e: Exception) {
                // Handle update failure
            }
        }
    }
}