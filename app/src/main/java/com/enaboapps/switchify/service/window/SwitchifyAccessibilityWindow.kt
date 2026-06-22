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
import com.enaboapps.switchify.service.llm.MediaPipeBackend
import com.enaboapps.switchify.service.utils.ScreenWatcher
import com.enaboapps.switchify.service.window.overlay.OverlayPlacement
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import com.enaboapps.switchify.service.window.overlay.SwitchifyOverlayHost

/**
 * This class manages the window for the Switchify accessibility service.
 * It handles adding and removing views, as well as managing the window state.
 */
class SwitchifyAccessibilityWindow private constructor() : LifecycleOwner, SavedStateRegistryOwner,
    SwitchifyOverlayHost {

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

    private val defaultDisplayTarget = OverlayTargets.defaultDisplay()

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
            MenuHighlightHud.instance.setup(context.applicationContext)
            ServiceStartupSplash.instance.setup(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error in setup: ${e.message}", e)
        }
    }

    /**
     * Creates the base layout.
     */
    private fun createBaseLayout() {
        getContext()?.let { context ->
            // Create a minimal layout without lifecycle owners to speed up initial creation
            // Lifecycle owners will be set when the view is actually added to the window
            baseLayout = RelativeLayout(context)
        }
    }

    /**
     * Sets up lifecycle owners for the base layout.
     * This is done after the view is added to reduce initial window creation overhead.
     */
    private fun setupLifecycleOwners() {
        baseLayout?.apply {
            setViewTreeLifecycleOwner(SwitchifyLifecycleOwner.getInstance())
            setViewTreeSavedStateRegistryOwner(SwitchifyLifecycleOwner.getInstance())
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
                MenuHighlightHud.instance.setup(context.applicationContext)
                ServiceStartupSplash.instance.setup(context.applicationContext)
                show()
            }
            val sleep = {
                ServiceMessageHUD.instance.dispose()
                MenuHighlightHud.instance.dispose()
                ServiceStartupSplash.instance.dispose()
                hide()
            }
            screenWatcher = ScreenWatcher(onScreenWake = wake, onScreenSleep = sleep)
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
                    // Add view with minimal overhead - lifecycle owners will be set up after
                    windowManager?.addView(layout, params)
                    isVisible = true

                    setupLifecycleOwners()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in show: ${e.message}", e)
            }
        }
    }

    /**
     * Hides the window without cleaning up views.
     */
    fun hide() {
        mainHandler.post {
            try {
                if (isVisible && baseLayout != null) {
                    windowManager?.removeView(baseLayout)
                    isVisible = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in hide: ${e.message}", e)
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
        MenuHighlightHud.instance.dispose()
        ServiceStartupSplash.instance.dispose()
        MediaPipeBackend.close()
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
        addView(
            target = defaultDisplayTarget,
            view = view,
            placement = OverlayPlacement.Bounds(
                x = x,
                y = y,
                width = width,
                height = height
            )
        )
    }

    override fun addView(
        target: OverlayTarget.Display,
        view: ViewGroup,
        placement: OverlayPlacement
    ) {
        mainHandler.post {
            try {
                if (target.displayId != OverlayTargets.DEFAULT_DISPLAY_ID) {
                    Log.w(TAG, "Display ${target.displayId} overlays fall back to default display")
                }
                val params = layoutParamsForPlacement(placement)
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
        addView(
            target = defaultDisplayTarget,
            view = view,
            placement = OverlayPlacement.WrapAt(x, y)
        )
    }

    /**
     * Adds a view to the bottom of the window.
     * @param view The view to add.
     * @param margins The margins to add to the view.
     */
    fun addViewToBottom(view: ViewGroup, margins: Int = 0) {
        addView(
            target = defaultDisplayTarget,
            view = view,
            placement = OverlayPlacement.BottomCentered(margins)
        )
    }

    /**
     * Adds a view to the top of the window, centered horizontally.
     * @param view The view to add.
     * @param margins The margins to add to the view.
     */
    fun addViewToTop(view: ViewGroup, margins: Int = 0) {
        addView(
            target = defaultDisplayTarget,
            view = view,
            placement = OverlayPlacement.TopCentered(margins)
        )
    }

    /**
     * Adds a view to the center of the window.
     * @param view The view to add.
     */
    fun addViewToCenter(view: ViewGroup) {
        addView(
            target = defaultDisplayTarget,
            view = view,
            placement = OverlayPlacement.Centered
        )
    }

    /**
     * Removes a view from the window.
     * @param view The view to remove.
     */
    fun removeView(view: ViewGroup) {
        removeView(defaultDisplayTarget, view)
    }

    override fun removeView(target: OverlayTarget.Display, view: ViewGroup) {
        mainHandler.post {
            try {
                if (target.displayId != OverlayTargets.DEFAULT_DISPLAY_ID) {
                    Log.w(TAG, "Display ${target.displayId} removal falls back to default display")
                }
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
        removeView(defaultDisplayTarget, id)
    }

    override fun removeView(target: OverlayTarget.Display, id: Int) {
        mainHandler.post {
            try {
                if (target.displayId != OverlayTargets.DEFAULT_DISPLAY_ID) {
                    Log.w(TAG, "Display ${target.displayId} removal by id falls back to default display")
                }
                baseLayout?.findViewById<ViewGroup>(id)?.let { view ->
                    baseLayout?.removeView(view)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in removeView by id: ${e.message}", e)
            }
        }
    }

    private fun layoutParamsForPlacement(placement: OverlayPlacement): RelativeLayout.LayoutParams {
        return when (placement) {
            is OverlayPlacement.Bounds -> RelativeLayout.LayoutParams(
                placement.width,
                placement.height
            ).apply {
                leftMargin = placement.x
                topMargin = placement.y
            }

            is OverlayPlacement.WrapAt -> RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = placement.x
                topMargin = placement.y
            }

            is OverlayPlacement.BottomCentered -> RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(
                    placement.margins,
                    placement.margins,
                    placement.margins,
                    placement.margins
                )
            }

            is OverlayPlacement.TopCentered -> RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_HORIZONTAL)
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
                setMargins(
                    placement.margins,
                    placement.margins,
                    placement.margins,
                    placement.margins
                )
            }

            OverlayPlacement.Centered -> RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
        }
    }
}
