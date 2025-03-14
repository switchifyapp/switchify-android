package com.enaboapps.switchify.service.techniques.cursor

import android.content.Context
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.techniques.shared.ScanMethodUIConstants
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * CursorUI class handles the creation, updating, and removal of cursor lines and quadrants
 * for the Switchify accessibility service.
 *
 * @property context The application context.
 */
class CursorUI(private val context: Context) : AccessTechniqueUIBase() {
    // Cursor lines, quadrants, and outlines
    private var xCursorLine: RelativeLayout? = null
    private var yCursorLine: RelativeLayout? = null
    private var xQuadrant: RelativeLayout? = null
    private var yQuadrant: RelativeLayout? = null
    private var xQuadrantOutline: RelativeLayout? = null
    private var yQuadrantOutline: RelativeLayout? = null

    companion object {
        private const val QUADRANT_ALPHA = 0.5f

        /**
         * Determines the number of quadrants based on the cursor mode.
         *
         * @return The number of quadrants (1 for single mode, 4 for block mode).
         */
        fun getNumberOfQuadrants(): Int {
            return if (CursorMode.isSingleMode()) 1 else 4
        }
    }

    /**
     * Calculates the width of a quadrant based on the cursor mode.
     *
     * @return The width of a quadrant in pixels.
     */
    fun getQuadrantWidth(): Int {
        return if (CursorMode.isBlockMode()) {
            CursorBounds.width(context) / getNumberOfQuadrants()
        } else {
            CursorBounds.width(context)
        }
    }

    /**
     * Calculates the height of a quadrant based on the cursor mode.
     *
     * @return The height of a quadrant in pixels.
     */
    fun getQuadrantHeight(): Int {
        return if (CursorMode.isBlockMode()) {
            CursorBounds.height(context) / getNumberOfQuadrants()
        } else {
            CursorBounds.height(context)
        }
    }

    /**
     * Shows or updates the x quadrant outline.
     *
     * @param quadrantNumber The quadrant number to position the quadrant.
     */
    private fun showXQuadrantOutline(quadrantNumber: Int) {
        if (CursorMode.isBlockMode()) {
            val xPosition = quadrantNumber * getQuadrantWidth()
            val yPosition = CursorBounds.Y_MIN
            val width = getQuadrantWidth()
            val height = ScreenUtils.getHeight(context)

            if (xQuadrantOutline == null) {
                xQuadrantOutline = RelativeLayout(context).apply {
                    background = QuadrantOutlineDrawable(context)
                }
                xQuadrantOutline?.let {
                    super.addView(it, xPosition, yPosition, width, height)
                }
            } else {
                updateXQuadrantOutline(quadrantNumber)
            }
        }
    }

    /**
     * Updates the x quadrant outline.
     *
     * @param quadrantNumber The quadrant number to position the quadrant.
     */
    private fun updateXQuadrantOutline(quadrantNumber: Int) {
        if (CursorMode.isBlockMode()) {
            val xPosition = quadrantNumber * getQuadrantWidth()
            val yPosition = CursorBounds.Y_MIN
            val width = getQuadrantWidth()
            val height = ScreenUtils.getHeight(context)

            xQuadrantOutline?.let {
                super.updateView(it, xPosition, yPosition, width, height)
            }
        }
    }

    /**
     * Shows or updates the y quadrant outline.
     *
     * @param quadrantNumber The quadrant number to position the quadrant.
     */
    private fun showYQuadrantOutline(quadrantNumber: Int) {
        if (CursorMode.isBlockMode()) {
            val xPosition = CursorBounds.X_MIN
            val yPosition = quadrantNumber * getQuadrantHeight()
            val width = ScreenUtils.getWidth(context)
            val height = getQuadrantHeight()

            if (yQuadrantOutline == null) {
                yQuadrantOutline = RelativeLayout(context).apply {
                    background = QuadrantOutlineDrawable(context)
                }
                yQuadrantOutline?.let {
                    super.addView(it, xPosition, yPosition, width, height)
                }
            } else {
                updateYQuadrantOutline(quadrantNumber)
            }
        }
    }

    /**
     * Updates the y quadrant outline.
     *
     * @param quadrantNumber The quadrant number to position the quadrant.
     */
    private fun updateYQuadrantOutline(quadrantNumber: Int) {
        if (CursorMode.isBlockMode()) {
            val xPosition = CursorBounds.X_MIN
            val yPosition = quadrantNumber * getQuadrantHeight()
            val width = ScreenUtils.getWidth(context)
            val height = getQuadrantHeight()

            yQuadrantOutline?.let {
                super.updateView(it, xPosition, yPosition, width, height)
            }
        }
    }

    /**
     * Shows or updates the horizontal cursor line.
     *
     * @param x The x-coordinate for the line.
     */
    fun showXCursorLine(x: Int) {
        val yPosition = CursorBounds.Y_MIN
        val height = CursorBounds.height(context)

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

        // Show the quadrant outline
        showXQuadrantOutline(x / getQuadrantWidth())
    }

    /**
     * Shows or updates the vertical cursor line.
     *
     * @param y The y-coordinate for the line.
     */
    fun showYCursorLine(y: Int) {
        val xPosition = CursorBounds.X_MIN
        val width = CursorBounds.width(context)

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

        // Show the quadrant outline
        showYQuadrantOutline(y / getQuadrantHeight())
    }

    /**
     * Shows or updates the horizontal quadrant.
     *
     * @param quadrantNumber The quadrant number to position the quadrant.
     */
    fun showXQuadrant(quadrantNumber: Int) {
        val yPosition = CursorBounds.Y_MIN
        val height = CursorBounds.height(context)
        val xPosition = quadrantNumber * getQuadrantWidth()

        if (xQuadrant == null) {
            xQuadrant = RelativeLayout(context).apply {
                setBackgroundColor(
                    ScanColorManager.getScanColorSetFromPreferences(
                        context
                    ).primaryColor.toColorInt()
                )
                alpha = QUADRANT_ALPHA
            }
            xQuadrant?.let {
                super.addView(it, xPosition, yPosition, getQuadrantWidth(), height)
            }
        } else {
            updateXQuadrant(quadrantNumber)
        }

        removeXQuadrantOutline()
        removeYQuadrantOutline()
    }

    /**
     * Shows or updates the vertical quadrant.
     *
     * @param quadrantNumber The quadrant number to position the quadrant.
     */
    fun showYQuadrant(quadrantNumber: Int) {
        val xPosition = CursorBounds.X_MIN
        val width = CursorBounds.width(context)
        val yPosition = CursorBounds.Y_MIN + (quadrantNumber * getQuadrantHeight())

        if (yQuadrant == null) {
            yQuadrant = RelativeLayout(context).apply {
                setBackgroundColor(
                    ScanColorManager.getScanColorSetFromPreferences(
                        context
                    ).primaryColor.toColorInt()
                )
                alpha = QUADRANT_ALPHA
            }
            yQuadrant?.let {
                super.addView(it, xPosition, yPosition, width, getQuadrantHeight())
            }
        } else {
            updateYQuadrant(quadrantNumber)
        }

        removeXQuadrantOutline()
        removeYQuadrantOutline()
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
     * Removes the horizontal quadrant.
     */
    fun removeXQuadrant() {
        xQuadrant?.let {
            super.removeView(it)
            xQuadrant = null
        }
    }

    /**
     * Removes the vertical quadrant.
     */
    fun removeYQuadrant() {
        yQuadrant?.let {
            super.removeView(it)
            yQuadrant = null
        }
    }

    /**
     * Removes the x quadrant outline.
     */
    fun removeXQuadrantOutline() {
        xQuadrantOutline?.let {
            super.removeView(it)
            xQuadrantOutline = null
        }
    }

    /**
     * Removes the y quadrant outline.
     */
    fun removeYQuadrantOutline() {
        yQuadrantOutline?.let {
            super.removeView(it)
            yQuadrantOutline = null
        }
    }

    /**
     * Updates the position of the horizontal cursor line.
     *
     * @param x The new x-coordinate for the line.
     */
    private fun updateXCursorLine(x: Int) {
        val width = ScanMethodUIConstants.LINE_THICKNESS
        val height = CursorBounds.height(context)
        xCursorLine?.let {
            super.updateView(it, x, CursorBounds.Y_MIN, width, height)
        }
    }

    /**
     * Updates the position of the vertical cursor line.
     *
     * @param y The new y-coordinate for the line.
     */
    private fun updateYCursorLine(y: Int) {
        val width = CursorBounds.width(context)
        val height = ScanMethodUIConstants.LINE_THICKNESS
        yCursorLine?.let {
            super.updateView(it, CursorBounds.X_MIN, y, width, height)
        }
    }

    /**
     * Updates the position of the horizontal quadrant.
     *
     * @param quadrantNumber The new quadrant number for positioning.
     */
    private fun updateXQuadrant(quadrantNumber: Int) {
        val quadrantWidth = getQuadrantWidth()
        val xPosition = quadrantNumber * quadrantWidth
        val width = quadrantWidth
        val height = CursorBounds.height(context)
        xQuadrant?.let { view ->
            super.updateView(view, xPosition, CursorBounds.Y_MIN, width, height)
        }
    }

    /**
     * Updates the position of the vertical quadrant.
     *
     * @param quadrantNumber The new quadrant number for positioning.
     */
    private fun updateYQuadrant(quadrantNumber: Int) {
        val quadrantHeight = getQuadrantHeight()
        val yPosition = CursorBounds.Y_MIN + (quadrantNumber * quadrantHeight)
        val width = CursorBounds.width(context)
        val height = quadrantHeight
        yQuadrant?.let { view ->
            super.updateView(view, CursorBounds.X_MIN, yPosition, width, height)
        }
    }

    /**
     * Checks if all cursor UI elements are removed.
     *
     * @return True if all elements are removed, false otherwise.
     */
    fun isReset(): Boolean {
        return xCursorLine == null
                && yCursorLine == null
                && xQuadrant == null
                && yQuadrant == null
                && xQuadrantOutline == null
                && yQuadrantOutline == null
    }

    /**
     * Removes all cursor UI elements.
     */
    fun reset() {
        removeXQuadrantOutline()
        removeYQuadrantOutline()
        removeXCursorLine()
        removeYCursorLine()
        removeXQuadrant()
        removeYQuadrant()
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

    /**
     * Checks if the horizontal quadrant is visible.
     *
     * @return True if visible, false otherwise.
     */
    fun isXQuadrantVisible(): Boolean = xQuadrant != null

    /**
     * Checks if the vertical quadrant is visible.
     *
     * @return True if visible, false otherwise.
     */
    fun isYQuadrantVisible(): Boolean = yQuadrant != null
}