package com.enaboapps.switchify.service.window

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.enaboapps.switchify.service.core.SwitchifyLifecycleOwner
import com.enaboapps.switchify.service.utils.ScreenWatcher

/**
 * This class manages the window for the Switchify accessibility service.
 * It handles adding and removing views, as well as managing the window state.
 */
class SwitchifyAccessibilityWindow private constructor() : LifecycleOwner, SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var baseLayout: RelativeLayout? = null
    private var context: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenWatcher: ScreenWatcher? = null
    private var isVisible = false

    companion object {
        private const val TAG = "SwitchifyAccessibilityWindow"
        val instance: SwitchifyAccessibilityWindow by lazy {
            SwitchifyAccessibilityWindow()
        }
    }

    override val lifecycle: Lifecycle
        get() = SwitchifyLifecycleOwner.getInstance().lifecycle

    override val savedStateRegistry: SavedStateRegistry
        get() = SwitchifyLifecycleOwner.getInstance().savedStateRegistry

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
            createBaseLayout()
            registerScreenWatcher()
            ServiceMessageHUD.instance.setup(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error in setup: ${e.message}", e)
        }
    }

    /**
     * Creates the base layout.
     */
    private fun createBaseLayout() {
        getContext()?.let { context ->
            baseLayout = RelativeLayout(context).apply {
                setViewTreeLifecycleOwner(SwitchifyLifecycleOwner.getInstance())
                setViewTreeSavedStateRegistryOwner(SwitchifyLifecycleOwner.getInstance())
            }
        }
    }

    /**
     * Registers with the screen watcher.
     */
    private fun registerScreenWatcher() {
        if (screenWatcher == null) {
            val context = getContext() ?: return
            val wake = {
                ServiceMessageHUD.instance.setup(context.applicationContext)
                createBaseLayout()
                show()
            }
            val sleep = {
                ServiceMessageHUD.instance.dispose()
                cleanup()
            }
            screenWatcher = ScreenWatcher(onScreenWake = wake, onScreenSleep = { cleanup() })
            screenWatcher?.register(context)
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
                for (i in 0 until (baseLayout?.childCount ?: 0)) {
                    val child = baseLayout?.getChildAt(i)
                    if (child is ViewGroup) {
                        child.removeAllViews()
                    }
                }

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
        ServiceMessageHUD.instance.dispose()
        cleanup()
        isVisible = false // Ensure the flag is set to false for the next time the window is created
        val ctx = getContext() ?: return
        screenWatcher?.unregister(ctx)
        screenWatcher = null
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