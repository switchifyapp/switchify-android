package com.enaboapps.switchify.service.techniques.cursor.line

import android.content.Context
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.techniques.shared.ScanMethodUIConstants
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * LineUI class handles the creation, updating, and removal of cursor lines
 * for the Switchify accessibility service.
 *
 * @property context The application context.
 */
class LineUI(private val context: Context) : AccessTechniqueUIBase() {
    // Cursor lines
    private var xCursorLine: RelativeLayout? = null
    private var yCursorLine: RelativeLayout? = null

    /**
     * Shows or updates the horizontal cursor line.
     *
     * @param x The x-coordinate for the line.
     */
    fun showXCursorLine(x: Int) {
        val yPosition = 0
        val height = ScreenUtils.getHeight(context)

        if (xCursorLine == null) {
            xCursorLine = RelativeLayout(context).apply {
                val color = ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor
                setBackgroundColor(color.toColorInt())
            }
            xCursorLine?.let {
                super.addView(
                    it,
                    x,
                    yPosition,
                    ScanMethodUIConstants.LINE_THICKNESS,
                    height
                )
            }
        } else {
            updateXCursorLine(x)
        }
    }

    /**
     * Shows or updates the vertical cursor line.
     *
     * @param y The y-coordinate for the line.
     */
    fun showYCursorLine(y: Int) {
        val xPosition = 0
        val width = ScreenUtils.getWidth(context)

        if (yCursorLine == null) {
            yCursorLine = RelativeLayout(context).apply {
                val color = ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor
                setBackgroundColor(color.toColorInt())
            }
            yCursorLine?.let {
                super.addView(
                    it,
                    xPosition,
                    y,
                    width,
                    ScanMethodUIConstants.LINE_THICKNESS
                )
            }
        } else {
            updateYCursorLine(y)
        }
    }

    /**
     * Removes the horizontal cursor line.
     */
    fun removeXCursorLine() {
        xCursorLine?.let {
            super.removeView(it)
            xCursorLine = null
        }
    }

    /**
     * Removes the vertical cursor line.
     */
    fun removeYCursorLine() {
        yCursorLine?.let {
            super.removeView(it)
            yCursorLine = null
        }
    }

    /**
     * Updates the position of the horizontal cursor line.
     *
     * @param x The new x-coordinate for the line.
     */
    private fun updateXCursorLine(x: Int) {
        val width = ScanMethodUIConstants.LINE_THICKNESS
        val height = ScreenUtils.getHeight(context)
        xCursorLine?.let {
            super.updateView(it, x, 0, width, height)
        }
    }

    /**
     * Updates the position of the vertical cursor line.
     *
     * @param y The new y-coordinate for the line.
     */
    private fun updateYCursorLine(y: Int) {
        val width = ScreenUtils.getWidth(context)
        val height = ScanMethodUIConstants.LINE_THICKNESS
        yCursorLine?.let {
            super.updateView(it, 0, y, width, height)
        }
    }

    /**
     * Checks if all cursor UI elements are removed.
     *
     * @return True if all elements are removed, false otherwise.
     */
    fun isReset(): Boolean {
        return xCursorLine == null && yCursorLine == null
    }

    /**
     * Removes all cursor UI elements.
     */
    fun reset() {
        removeXCursorLine()
        removeYCursorLine()
        super.hide()
    }

    /**
     * Checks if the horizontal cursor line is visible.
     *
     * @return True if visible, false otherwise.
     */
    fun isXCursorLineVisible(): Boolean = xCursorLine != null

    /**
     * Checks if the vertical cursor line is visible.
     *
     * @return True if visible, false otherwise.
     */
    fun isYCursorLineVisible(): Boolean = yCursorLine != null
} 