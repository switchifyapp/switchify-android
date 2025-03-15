package com.enaboapps.switchify.service.window

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RelativeLayout

/**
 * This class manages the window for the Switchify accessibility service.
 * It handles adding and removing views, as well as managing the window state.
 */
class SwitchifyAccessibilityWindow private constructor() {

    private var windowManager: WindowManager? = null
    private var baseLayout: RelativeLayout? = null
    private var context: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isVisible = false

    companion object {
        private const val TAG = "SwitchifyAccessibilityWindow"
        val instance: SwitchifyAccessibilityWindow by lazy {
            SwitchifyAccessibilityWindow()
        }
    }

    /**
     * Initializes the window and sets up the context and window manager.
     * @param context The context to use for the window.
     */
    fun setup(context: Context) {
        try {
            // Clean up previous state if it exists
            cleanup()

            this.context = context
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            baseLayout = RelativeLayout(context)
            ServiceMessageHUD.instance.setup(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error in setup: ${e.message}", e)
        }
    }

    /**
     * Gets the context of the window.
     * @return The context of the window.
     */
    fun getContext(): Context? {
        return context
    }

    /**
     * Shows the window.
     */
    fun show() {
        mainHandler.post {
            try {
                // Avoid adding duplicate views
                if (isVisible) {
                    Log.d(TAG, "Window already visible, skipping show()")
                    return@post
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

                baseLayout?.let { layout ->
                    windowManager?.addView(layout, params)
                    isVisible = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in show: ${e.message}", e)
            }
        }
    }

    /**
     * Cleans up the window and its resources.
     */
    fun cleanup() {
        mainHandler.post {
            try {
                if (isVisible && baseLayout != null) {
                    windowManager?.removeView(baseLayout)
                    isVisible = false
                }

                // Clear all children from baseLayout
                baseLayout?.removeAllViews()
            } catch (e: Exception) {
                Log.e(TAG, "Error in cleanup: ${e.message}", e)
            }
        }
    }

    /**
     * Cleans up the window and its resources when the service is destroyed.
     */
    fun onServiceDestroy() {
        cleanup()
        context = null
        windowManager = null
        baseLayout = null
    }

    /**
     * Adds a view to the window.
     * @param view The view to add.
     * @param x The x coordinate of the view.
     * @param y The y coordinate of the view.
     * @param width The width of the view.
     * @param height The height of the view.
     */
    fun addView(view: ViewGroup, x: Int, y: Int, width: Int, height: Int) {
        mainHandler.post {
            try {
                val params = RelativeLayout.LayoutParams(width, height)
                params.leftMargin = x
                params.topMargin = y
                baseLayout?.addView(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error in addView: ${e.message}", e)
            }
        }
    }

    /**
     * Adds a view to the window.
     * @param view The view to add.
     * @param x The x coordinate of the view.
     * @param y The y coordinate of the view.
     */
    fun addView(view: ViewGroup, x: Int, y: Int) {
        mainHandler.post {
            try {
                val params = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                )
                params.leftMargin = x
                params.topMargin = y
                baseLayout?.addView(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error in addView: ${e.message}", e)
            }
        }
    }

    /**
     * Adds a view to the bottom of the window.
     * @param view The view to add.
     * @param margins The margins to add to the view.
     */
    fun addViewToBottom(view: ViewGroup, margins: Int = 0) {
        mainHandler.post {
            try {
                val params = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                )
                params.addRule(RelativeLayout.CENTER_HORIZONTAL)
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                params.setMargins(margins, margins, margins, margins)
                baseLayout?.addView(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error in addViewToBottom: ${e.message}", e)
            }
        }
    }

    /**
     * Adds a view to the center of the window.
     * @param view The view to add.
     */
    fun addViewToCenter(view: ViewGroup) {
        mainHandler.post {
            try {
                val params = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                )
                params.addRule(RelativeLayout.CENTER_IN_PARENT)
                baseLayout?.addView(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error in addViewToCenter: ${e.message}", e)
            }
        }
    }

    /**
     * Removes a view from the window.
     * @param view The view to remove.
     */
    fun removeView(view: ViewGroup) {
        mainHandler.post {
            try {
                if (view.parent == baseLayout) {
                    baseLayout?.removeView(view)
                } else {
                    Log.w(TAG, "View is not attached to baseLayout, cannot remove")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in removeView: ${e.message}", e)
            }
        }
    }

    /**
     * Removes a view from the window by its id.
     * @param id The id of the view to remove.
     */
    fun removeView(id: Int) {
        mainHandler.post {
            try {
                baseLayout?.findViewById<ViewGroup>(id)?.let { view ->
                    baseLayout?.removeView(view)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in removeView by id: ${e.message}", e)
            }
        }
    }
}