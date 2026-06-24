package com.enaboapps.switchify.service.techniques

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets

/**
 * AccessTechniqueUIBase is the base class for all access technique UI classes.
 * It provides common functionality for managing the UI state.
 */
open class AccessTechniqueUIBase {
    private var view: RelativeLayout? = null
    private val childViews = mutableSetOf<ViewGroup>()
    private var overlayTarget: OverlayTarget.Display = OverlayTargets.defaultDisplay().copy(forceSurface = true)

    private val window = SwitchifyAccessibilityWindow.instance
    private val handler = Handler(Looper.getMainLooper())

    fun setOverlayTarget(target: OverlayTarget.Display) {
        overlayTarget = target.copy(forceSurface = true)
    }

    /**
     * Shows the window.
     */
    private fun show() {
        if (view == null) {
            window.getContext()?.let { context ->
                view = RelativeLayout(context)
                view?.let { layout ->
                    val metrics = window.getDisplayMetrics(overlayTarget)
                    val fallbackMetrics = context.resources.displayMetrics
                    window.addView(
                        overlayTarget,
                        layout,
                        0,
                        0,
                        metrics?.width ?: fallbackMetrics.widthPixels,
                        metrics?.height ?: fallbackMetrics.heightPixels
                    )
                }
            }
        }
    }

    /**
     * Adds a view to the window.
     *
     * @param view The view to add.
     * @param x The x coordinate of the view.
     * @param y The y coordinate of the view.
     * @param width The width of the view.
     * @param height The height of the view.
     */
    fun addView(view: ViewGroup, x: Int, y: Int, width: Int, height: Int) {
        handler.post {
            try {
                show()
                val params = createLayoutParams(x, y, width, height)
                view.layoutParams = params
                this.view?.addView(view)
                childViews.add(view)
            } catch (e: Exception) {
                childViews.remove(view)
                e.printStackTrace()
            }
        }
    }

    /**
     * Adds a view directly without posting to handler.
     * Should only be called when already on main thread.
     *
     * @param view The view to add.
     * @param x The x coordinate of the view.
     * @param y The y coordinate of the view.
     * @param width The width of the view.
     * @param height The height of the view.
     */
    protected fun addViewDirectly(view: ViewGroup, x: Int, y: Int, width: Int, height: Int) {
        try {
            show()
            val params = createLayoutParams(x, y, width, height)
            view.layoutParams = params
            this.view?.addView(view)
            childViews.add(view)
        } catch (e: Exception) {
            childViews.remove(view)
            e.printStackTrace()
        }
    }

    /**
     * Updates a view's position and size.
     *
     * @param view The view to update.
     * @param x The x coordinate of the view.
     * @param y The y coordinate of the view.
     * @param width The width of the view.
     * @param height The height of the view.
     */
    fun updateView(view: ViewGroup, x: Int, y: Int, width: Int, height: Int) {
        handler.post {
            try {
                var params = view.layoutParams as RelativeLayout.LayoutParams
                params.leftMargin = x
                params.topMargin = y
                params.width = width
                params.height = height
                view.layoutParams = params
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Removes a view from the UI.
     *
     * @param view The view to remove.
     */
    fun removeView(view: ViewGroup) {
        handler.post {
            try {
                removeViewSafely(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Safely removes a view with parent validation and tracking cleanup.
     *
     * @param view The view to remove safely.
     */
    private fun removeViewSafely(view: ViewGroup) {
        if (view.parent != null) {
            this.view?.removeView(view)
        }
        childViews.remove(view)
    }

    /**
     * Removes all child views safely to prevent orphans.
     */
    fun removeAllChildViews() {
        handler.post {
            try {
                childViews.toList().forEach { view ->
                    removeViewSafely(view)
                }
                childViews.clear()
            } catch (e: Exception) {
                childViews.clear()
                e.printStackTrace()
            }
        }
    }

    /**
     * Hides the window and clears all child views.
     */
    fun hide() {
        handler.post {
            view?.let {
                window.removeView(overlayTarget, it)
                view = null
            }
            childViews.clear()
        }
    }


    /**
     * Helper function to create a new RelativeLayout.LayoutParams object.
     *
     * @param x The x-coordinate of the layout.
     * @param y The y-coordinate of the layout.
     * @param width The width of the layout.
     * @param height The height of the layout.
     * @return The new RelativeLayout.LayoutParams object.
     */
    private fun createLayoutParams(
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): RelativeLayout.LayoutParams {
        return RelativeLayout.LayoutParams(width, height).apply {
            leftMargin = x
            topMargin = y
        }
    }
}
