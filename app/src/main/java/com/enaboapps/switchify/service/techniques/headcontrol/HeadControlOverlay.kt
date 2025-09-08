package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.R
import android.util.Log
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.scanning.ScanColorManager

class HeadControlOverlay(private val context: Context) {
    companion object {
        private const val TAG = "HeadControlOverlay"
    }
    private var windowManager: WindowManager? = null
    private var overlayView: ImageView? = null
    private var isVisible = false
    private var isAddingView = false // Prevent concurrent additions
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun createOverlayView() {
        overlayView = ImageView(context).apply {
            setImageResource(R.drawable.ic_reselect) // Using existing icon
            imageTintList = ColorStateList.valueOf(ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor.toColorInt())
            scaleX = 1.2f
            scaleY = 1.2f
            alpha = 0.8f
        }
    }

    fun showPointer(x: Int, y: Int) {
        Log.d(TAG, "showPointer called at $x, $y (isVisible: $isVisible, isAddingView: $isAddingView)")
        mainHandler.post {
            overlayView?.let { view ->
                if (!isVisible && !isAddingView) {
                    Log.d(TAG, "Adding overlay to window")
                    isAddingView = true
                    addToWindow(view)
                    isVisible = true
                    isAddingView = false
                } else if (isVisible && !isAddingView) {
                    Log.d(TAG, "Overlay already visible, just updating position")
                    updatePosition(view, x, y)
                } else {
                    Log.d(TAG, "Overlay addition in progress, skipping")
                }
            } ?: run {
                Log.e(TAG, "overlayView is null!")
            }
        }
    }

    fun hidePointer() {
        Log.d(TAG, "hidePointer called (isVisible: $isVisible)")
        mainHandler.post {
            overlayView?.let { view ->
                if (isVisible) {
                    try {
                        windowManager?.removeView(view)
                        isVisible = false
                        isAddingView = false // Reset flag
                        Log.d(TAG, "Overlay removed from window")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to remove overlay: ${e.message}")
                        // View might already be removed, force flags to false
                        isVisible = false
                        isAddingView = false
                    }
                } else {
                    Log.d(TAG, "Overlay not visible, skipping remove")
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
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager?.addView(view, params)
            Log.d(TAG, "Successfully added overlay view to window")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view to window: ${e.message}", e)
            // Reset flags on failure
            isVisible = false
            isAddingView = false
            // Common causes:
            // 1. Permission denied - accessibility service may not have overlay permissions
            // 2. View already added - try removing first
            // 3. Window manager not available
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