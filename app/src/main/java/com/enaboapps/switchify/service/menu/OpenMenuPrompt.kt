package com.enaboapps.switchify.service.menu

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

/**
 * This class manages the prompt to ask the user if they want to open the menu.
 */
class OpenMenuPrompt {
    private var menuPromptView: RelativeLayout? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isShowing = false

    companion object {
        val instance: OpenMenuPrompt by lazy {
            OpenMenuPrompt()
        }

        private const val TAG = "OpenMenuPrompt"
    }

    fun show(context: Context) {
        if (isShowing) return

        mainHandler.post {
            createPromptView(context)
            menuPromptView?.let { SwitchifyAccessibilityWindow.instance.addViewToCenter(it) }
            isShowing = true
        }
    }

    fun hide() {
        if (!isShowing) return

        mainHandler.post {
            menuPromptView?.let { view ->
                SwitchifyAccessibilityWindow.instance.removeView(view)
                isShowing = false
            }
        }
    }

    private fun createPromptView(context: Context): RelativeLayout {
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
        menuPromptView = rootLayout
        return rootLayout
    }
} 