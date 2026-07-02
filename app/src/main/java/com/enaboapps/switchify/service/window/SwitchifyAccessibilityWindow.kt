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
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.llm.MediaPipeBackend
import com.enaboapps.switchify.service.menu.MenuViewHandler
import com.enaboapps.switchify.service.utils.ScreenWatcher
import com.enaboapps.switchify.service.window.overlay.OverlayPlacement
import com.enaboapps.switchify.service.window.overlay.OverlayDisplayMetrics
import com.enaboapps.switchify.service.window.overlay.OverlayDisplayMetricsProvider
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import com.enaboapps.switchify.service.window.overlay.OverlayHandle
import com.enaboapps.switchify.service.window.overlay.OverlaySurfacePolicy
import com.enaboapps.switchify.service.window.overlay.SurfaceOverlayLimit
import com.enaboapps.switchify.service.window.overlay.SurfaceControlOverlayBackend
import com.enaboapps.switchify.service.window.overlay.SwitchifyOverlayHost
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    private var windowGeneration = 0
    private var rootOverlayId: String? = null
    private var serviceEpoch = 0L
    private var overlaySequence = 0L
    private var acceptingOverlayOperations = false
    private val windowStateCleaner = WindowStateCleaner(
        onCleanupStarted = { state, rootAttached ->
            Log.d(
                TAG,
                "Cleaning window generation=${state.generation} surfaceHandles=${state.surfaceHandles.size} rootVisible=${state.wasVisible} rootAttached=$rootAttached"
            )
        },
        onHandleReleased = { key ->
            Log.d(TAG, "Released captured surface overlay key=${System.identityHashCode(key)}")
        },
        onHandleReleaseFailed = { key, throwable ->
            Log.w(
                TAG,
                "Failed to release captured surface overlay key=${System.identityHashCode(key)}",
                throwable
            )
        },
        onRootRemoved = { id ->
            SwitchifyOverlayDebugRegistry.recordRootRemoved(id)
            Log.d(TAG, "Removed WindowManager root overlay id=$id")
        },
        onRootAlreadyRemoved = { id, throwable ->
            SwitchifyOverlayDebugRegistry.recordRootRemoved(id)
            Log.w(TAG, "WindowManager root overlay already removed id=$id", throwable)
        },
        onRootRemoveFailed = { id, throwable ->
            Log.e(TAG, "Failed to remove WindowManager root overlay id=$id", throwable)
        }
    )

    companion object {
        private const val TAG = "SwitchifyAccessibilityWindow"
        private const val SHUTDOWN_CLEANUP_TIMEOUT_MS = 2_000L
        val instance: SwitchifyAccessibilityWindow by lazy {
            SwitchifyAccessibilityWindow()
        }
    }

    private data class WindowState(
        val generation: Int,
        val windowManager: WindowManager?,
        val baseLayout: RelativeLayout?,
        val surfaceHandles: MutableMap<ViewGroup, OverlayHandle>,
        val wasVisible: Boolean,
        val rootOverlayId: String?
    )

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
            acceptingOverlayOperations = false
            serviceEpoch = PreferenceManager(context.applicationContext).nextOverlayServiceEpoch()
            overlaySequence = 0L
            windowGeneration += 1
            if (!cleanupOnMainBlocking()) {
                Log.w(TAG, "Timed out while cleaning up previous window state before setup")
            }

            this.context = context
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            surfaceControlBackend = (context as? AccessibilityService)?.let { service ->
                SurfaceControlOverlayBackend(
                    service = service,
                    hostTokenProvider = { baseLayout?.windowToken },
                    identityProvider = { target, view ->
                        nextOverlayIdentity(stableOverlayIdFor(target, view))
                    }
                )
            }
            createBaseLayout()
            acceptingOverlayOperations = true
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
        postIfCurrentGeneration {
            try {
                if (isVisible) {
                    Log.d(TAG, "Window already visible, skipping show()")
                    return@postIfCurrentGeneration
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
                val identity = nextOverlayIdentity("root:default-display")
                val title = "Switchify:${identity.diagnosticId}"
                params.setTitle(title)

                baseLayout?.let { layout ->
                    // Add view with minimal overhead - lifecycle owners will be set up after
                    windowManager?.addView(layout, params)
                    isVisible = true
                    rootOverlayId = SwitchifyOverlayDebugRegistry.recordRootShown(
                        identity = identity,
                        title = title,
                        viewId = layout.id,
                        viewClass = layout.javaClass.name,
                        handleIdentityHash = System.identityHashCode(layout)
                    )
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
        postIfCurrentGeneration {
            try {
                if (isVisible && baseLayout != null) {
                    windowManager?.removeView(baseLayout)
                    isVisible = false
                    SwitchifyOverlayDebugRegistry.recordRootRemoved(rootOverlayId)
                    Log.d(TAG, "Removed WindowManager root overlay id=$rootOverlayId during hide")
                    rootOverlayId = null
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
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanupCurrentStateNow()
            return
        }
        mainHandler.post { cleanupCurrentStateNow() }
    }

    /**
     * Cleans up the window and its resources when the service is destroyed.
     */
    fun onServiceDestroy() {
        acceptingOverlayOperations = false
        windowGeneration += 1
        ServiceMessageHUD.instance.dispose()
        MenuHighlightHud.instance.dispose()
        ServiceStartupSplash.instance.dispose()
        MediaPipeBackend.close()
        if (!cleanupOnMainBlocking()) {
            Log.w(TAG, "Timed out waiting for service window cleanup")
        }
        getContext()?.let { ctx ->
            screenWatcher?.unregister(ctx)
        }
        screenWatcher = null
        context = null
        windowManager = null
        surfaceControlBackend = null
        baseLayout = null
        surfaceOverlayHandles.clear()
        rootOverlayId = null
        isVisible = false
    }

    fun dumpOverlayDebugState() {
        Log.d(TAG, SwitchifyOverlayDebugRegistry.snapshotText())
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
        postIfCurrentGeneration {
            try {
                if (shouldUseSurfaceBackend(target)) {
                    surfaceOverlayHandles.remove(view)?.release()
                    if (!SurfaceOverlayLimit.canAddSurfaceOverlay(surfaceOverlayHandles.size)) {
                        Log.w(
                            TAG,
                            "Surface overlay limit reached for $target view=${view.javaClass.name}"
                        )
                        if (!canUseDefaultRootFallback(target)) return@postIfCurrentGeneration
                    } else {
                        if (baseLayout?.windowToken == null) {
                            Log.w(TAG, "Surface overlay unavailable for $target because root token is missing")
                            if (target is OverlayTarget.Window) return@postIfCurrentGeneration
                        } else {
                            val handle = surfaceControlBackend?.attach(target, view, placement)
                            if (handle != null) {
                                handle.setVisible(isVisible)
                                surfaceOverlayHandles[view] = handle
                                return@postIfCurrentGeneration
                            }
                        }
                    }
                    if (target is OverlayTarget.Window) {
                        Log.w(TAG, "Window overlay unavailable for $target; skipping fallback")
                        return@postIfCurrentGeneration
                    }
                    if (!canUseDefaultRootFallback(target)) {
                        Log.w(TAG, "Surface overlay unavailable for $target; no default-root fallback")
                        return@postIfCurrentGeneration
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
        postIfCurrentGeneration {
            try {
                surfaceOverlayHandles.remove(view)?.let { handle ->
                    handle.release()
                    return@postIfCurrentGeneration
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
        postIfCurrentGeneration {
            try {
                surfaceOverlayHandles.entries.firstOrNull { it.key.id == id }?.let { entry ->
                    surfaceOverlayHandles.remove(entry.key)?.release()
                    return@postIfCurrentGeneration
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
        return OverlaySurfacePolicy.shouldUseSurfaceBackend(target)
    }

    private fun canUseDefaultRootFallback(target: OverlayTarget): Boolean {
        return target is OverlayTarget.Display &&
            target.displayId == OverlayTargets.DEFAULT_DISPLAY_ID
    }

    fun canAttachSurfaceOverlay(target: OverlayTarget): Boolean {
        return shouldUseSurfaceBackend(target) &&
            surfaceControlBackend?.canAttach(target) == true
    }

    private fun postIfCurrentGeneration(block: () -> Unit) {
        val generation = windowGeneration
        mainHandler.post {
            if (generation != windowGeneration || !acceptingOverlayOperations) return@post
            block()
        }
    }

    private fun nextOverlayIdentity(stableId: String): OverlayDebugIdentity {
        overlaySequence += 1L
        return OverlayDebugIdentity(
            stableId = stableId,
            serviceEpoch = serviceEpoch,
            generation = windowGeneration,
            sequence = overlaySequence
        )
    }

    private fun stableOverlayIdFor(target: OverlayTarget, view: ViewGroup): String {
        val role = when (view.id) {
            MenuViewHandler.VIEW_ID -> "menu"
            else -> view.javaClass.simpleName.ifEmpty { "view" }
        }
        return when (target) {
            is OverlayTarget.Display -> "surface:display:${target.displayId}:$role"
            is OverlayTarget.Window -> "surface:window:${target.displayId}:${target.accessibilityWindowId}:${target.windowType}:$role"
        }
    }

    private fun cleanupOnMainBlocking(): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanupCurrentStateNow()
            return true
        }
        val state = captureCurrentWindowState()
        val latch = CountDownLatch(1)
        mainHandler.postAtFrontOfQueue {
            try {
                cleanupSnapshotNow(state)
            } finally {
                latch.countDown()
            }
        }
        return latch.await(SHUTDOWN_CLEANUP_TIMEOUT_MS, TimeUnit.MILLISECONDS).also { completed ->
            if (!completed) {
                Log.w(TAG, "Timed out waiting for window cleanup")
            }
        }
    }

    private fun cleanupCurrentStateNow() {
        cleanupSnapshotNow(captureCurrentWindowState())
    }

    private fun captureCurrentWindowState(): WindowState {
        val state = WindowState(
            generation = windowGeneration,
            windowManager = windowManager,
            baseLayout = baseLayout,
            surfaceHandles = surfaceOverlayHandles.toMutableMap(),
            wasVisible = isVisible,
            rootOverlayId = rootOverlayId
        )
        surfaceOverlayHandles.clear()
        rootOverlayId = null
        isVisible = false
        return state
    }

    private fun cleanupSnapshotNow(state: WindowState) {
        windowStateCleaner.cleanup(
            WindowCleanupState(
                surfaceHandles = state.surfaceHandles.mapKeys { it.key as Any }
                    .mapValues { OverlayHandleCleanupHandle(it.value) }
                    .toMutableMap(),
                root = state.baseLayout?.let { layout ->
                    AndroidWindowCleanupRoot(state.windowManager, layout, state.rootOverlayId)
                },
                wasVisible = state.wasVisible,
                generation = state.generation
            )
        )
    }

    private class OverlayHandleCleanupHandle(
        private val handle: OverlayHandle
    ) : WindowCleanupHandle {
        override fun release() {
            handle.release()
        }
    }

    private class AndroidWindowCleanupRoot(
        private val windowManager: WindowManager?,
        private val layout: RelativeLayout,
        override val debugOverlayId: String?
    ) : WindowCleanupRoot {
        override val isAttachedToWindow: Boolean
            get() = layout.parent != null

        override fun removeDescendantViews() {
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (child is ViewGroup) {
                    child.removeAllViews()
                }
            }
            layout.removeAllViews()
        }

        override fun removeImmediately() {
            windowManager?.removeViewImmediate(layout)
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
