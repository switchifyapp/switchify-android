package com.enaboapps.switchify.service.techniques

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

/**
 * AccessTechniqueUIBase is the base class for all access technique UI classes.
 * It provides common functionality for managing the UI state.
 */
open class AccessTechniqueUIBase {
    private var view: RelativeLayout? = null

    private val window = SwitchifyAccessibilityWindow.instance
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Shows the window.
     */
    private fun show() {
        if (view == null) {
            window.getContext()?.let { context ->
                view = RelativeLayout(context)
                view?.let { layout ->
                    window.addView(
                        layout,
                        0,
                        0,
                        ScreenUtils.getWidth(context),
                        ScreenUtils.getHeight(context)
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                this.view?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Hides the window.
     */
    fun hide() {
        handler.post {
            view?.let {
                window.removeView(it)
                view = null
            }
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
    protected fun createLayoutParams(
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