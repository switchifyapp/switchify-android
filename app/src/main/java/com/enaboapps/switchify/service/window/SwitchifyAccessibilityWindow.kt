package com.enaboapps.switchify.service.window

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
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
import com.enaboapps.switchify.service.window.overlay.OverlayDisplayMetrics
import com.enaboapps.switchify.service.window.overlay.OverlayDisplayMetricsProvider
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import com.enaboapps.switchify.service.window.overlay.OverlayHandle
import com.enaboapps.switchify.service.window.overlay.SurfaceControlOverlayBackend
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
    private var surfaceControlBackend: SurfaceControlOverlayBackend? = null
    private val surfaceOverlayHandles = mutableMapOf<ViewGroup, OverlayHandle>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenWatcher: ScreenWatcher? = null
    private var isVisible = false

    companion object {
        private const val TAG = "SwitchifyAccessibilityWindow"
        val instance: SwitchifyAccessibilityWindow by lazy {
            SwitchifyAccessibilityWindow()
        }
    }

    private val defaultDisplayTarget = OverlayTargets.defaultDisplay().copy(forceSurface = true)

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
            surfaceControlBackend = (context as? AccessibilityService)?.let { service ->
                SurfaceControlOverlayBackend(service) { baseLayout?.windowToken }
            }
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

    fun getDisplayMetrics(target: OverlayTarget): OverlayDisplayMetrics? {
        val context = getContext() ?: return null
        val displayId = when (target) {
            is OverlayTarget.Display -> target.displayId
            is OverlayTarget.Window -> target.displayId
        }
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displayContext = displayManager
            ?.getDisplay(displayId)
            ?.let { context.createDisplayContext(it) }
            ?: context
        return OverlayDisplayMetricsProvider.displayMetrics(displayContext, displayId)
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
                    surfaceOverlayHandles.values.forEach { it.setVisible(true) }

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
                surfaceOverlayHandles.values.forEach { it.setVisible(false) }
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
                releaseSurfaceOverlays()
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
        surfaceControlBackend = null
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

    fun addView(
        target: OverlayTarget.Display,
        view: ViewGroup,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        addView(
            target = target,
            view = view,
            placement = OverlayPlacement.Bounds(
                x = x,
                y = y,
                width = width,
                height = height
            )
        )
    }

    fun addView(
        target: OverlayTarget,
        view: ViewGroup,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        addView(
            target = target,
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
        addView(target as OverlayTarget, view, placement)
    }

    fun addView(
        target: OverlayTarget,
        view: ViewGroup,
        placement: OverlayPlacement
    ) {
        mainHandler.post {
            try {
                if (shouldUseSurfaceBackend(target)) {
                    val handle = surfaceControlBackend?.attach(target, view, placement)
                    if (handle != null) {
                        handle.setVisible(isVisible)
                        surfaceOverlayHandles[view] = handle
                        return@post
                    }
                    if (target is OverlayTarget.Window) {
                        Log.w(TAG, "Window overlay unavailable for $target; skipping fallback")
                        return@post
                    }
                    if (!canUseDefaultRootFallback(target)) {
                        Log.w(TAG, "Surface overlay unavailable for $target; no default-root fallback")
                        return@post
                    }
                    Log.w(TAG, "Surface overlay unavailable for $target; falling back to default overlay")
                }
                val params = layoutParamsForPlacement(placement)
                baseLayout?.addView(view, params)
                Log.d(TAG, "Added overlay to default WindowManager root for $target")
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

    fun addView(target: OverlayTarget.Display, view: ViewGroup, x: Int, y: Int) {
        addView(
            target = target,
            view = view,
            placement = OverlayPlacement.WrapAt(x, y)
        )
    }

    fun addView(target: OverlayTarget, view: ViewGroup, x: Int, y: Int) {
        addView(
            target = target,
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
        addViewToBottom(defaultDisplayTarget, view, margins)
    }

    fun addViewToBottom(target: OverlayTarget.Display, view: ViewGroup, margins: Int = 0) {
        addView(
            target = target,
            view = view,
            placement = OverlayPlacement.BottomCentered(margins)
        )
    }

    fun addViewToBottom(target: OverlayTarget, view: ViewGroup, margins: Int = 0) {
        addView(
            target = target,
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
        addViewToTop(defaultDisplayTarget, view, margins)
    }

    fun addViewToTop(target: OverlayTarget.Display, view: ViewGroup, margins: Int = 0) {
        addView(
            target = target,
            view = view,
            placement = OverlayPlacement.TopCentered(margins)
        )
    }

    fun addViewToTop(target: OverlayTarget, view: ViewGroup, margins: Int = 0) {
        addView(
            target = target,
            view = view,
            placement = OverlayPlacement.TopCentered(margins)
        )
    }

    /**
     * Adds a view to the center of the window.
     * @param view The view to add.
     */
    fun addViewToCenter(view: ViewGroup) {
        addViewToCenter(defaultDisplayTarget, view)
    }

    fun addViewToCenter(target: OverlayTarget.Display, view: ViewGroup) {
        addView(
            target = target,
            view = view,
            placement = OverlayPlacement.Centered
        )
    }

    fun addViewToCenter(target: OverlayTarget, view: ViewGroup) {
        addView(
            target = target,
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
        removeView(target as OverlayTarget, view)
    }

    fun removeView(target: OverlayTarget, view: ViewGroup) {
        mainHandler.post {
            try {
                surfaceOverlayHandles.remove(view)?.let { handle ->
                    handle.release()
                    return@post
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
                surfaceOverlayHandles.entries.firstOrNull { it.key.id == id }?.let { entry ->
                    surfaceOverlayHandles.remove(entry.key)?.release()
                    return@post
                }
                baseLayout?.findViewById<ViewGroup>(id)?.let { view ->
                    baseLayout?.removeView(view)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in removeView by id: ${e.message}", e)
            }
        }
    }

    private fun shouldUseSurfaceBackend(target: OverlayTarget): Boolean {
        return when (target) {
            is OverlayTarget.Display -> target.forceSurface ||
                target.displayId != OverlayTargets.DEFAULT_DISPLAY_ID
            is OverlayTarget.Window -> true
        }
    }

    private fun canUseDefaultRootFallback(target: OverlayTarget): Boolean {
        return target is OverlayTarget.Display &&
            target.displayId == OverlayTargets.DEFAULT_DISPLAY_ID
    }

    fun canAttachSurfaceOverlay(target: OverlayTarget): Boolean {
        return shouldUseSurfaceBackend(target) &&
            surfaceControlBackend?.canAttach(target) == true
    }

    private fun releaseSurfaceOverlays() {
        surfaceOverlayHandles.values.forEach { handle ->
            try {
                handle.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing surface overlay", e)
            }
        }
        surfaceOverlayHandles.clear()
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
