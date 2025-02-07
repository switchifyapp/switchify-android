package com.enaboapps.switchify.service.menu

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

class MenuPopup {
    private var popupView: RelativeLayout? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isShowing = false

    companion object {
        val instance: MenuPopup by lazy {
            MenuPopup()
        }

        private const val TAG = "MenuPopup"
    }

    fun show(context: Context) {
        Log.d(TAG, "Attempting to show menu popup")
        if (isShowing) return

        Log.d(TAG, "Showing menu popup")

        mainHandler.post {
            createPopupView(context)
            SwitchifyAccessibilityWindow.instance.addViewToCenter(popupView!!)
            isShowing = true
        }
    }

    fun hide() {
        Log.d(TAG, "Attempting to hide menu popup")
        if (!isShowing) return

        Log.d(TAG, "Hiding menu popup")

        mainHandler.post {
            popupView?.let { view ->
                SwitchifyAccessibilityWindow.instance.removeView(view)
                isShowing = false
            }
        }
    }

    private fun createPopupView(context: Context): RelativeLayout {
        // Create root RelativeLayout with background
        val rootLayout = RelativeLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            background = ContextCompat.getDrawable(context, R.drawable.menu_background)

            // Add padding around the content
            val padding = 50
            setPadding(padding, padding, padding, padding)
        }

        // Create TextView for the message with improved styling
        val messageText = TextView(context).apply {
            text = "Press to open menu"
            setTextColor(Color.WHITE)
            textSize = 18f // Slightly larger text
            gravity = Gravity.CENTER

            // Set layout parameters for the TextView
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            layoutParams = params
        }

        rootLayout.addView(messageText)
        popupView = rootLayout
        return rootLayout
    }
} 