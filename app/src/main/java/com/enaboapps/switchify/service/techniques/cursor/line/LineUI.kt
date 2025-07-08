package com.enaboapps.switchify.service.techniques.cursor.line

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.techniques.cursor.blocks.CursorBlock
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
    private var blockOutline: RelativeLayout? = null
    private var currentBlock: CursorBlock? = null

    private fun getScreenBounds(): Rect {
        return Rect(0, 0, ScreenUtils.getWidth(context), ScreenUtils.getHeight(context))
    }

    private fun getBounds(): Rect {
        return currentBlock?.let { block ->
            Rect(block.left, block.top, block.right, block.bottom)
        } ?: getScreenBounds()
    }

    private fun showBlockOutline() {
        val bounds = getBounds()
        if (currentBlock != null) {
            if (blockOutline == null) {
                val strokeThickness = ScanMethodUIConstants.LINE_THICKNESS
                blockOutline = RelativeLayout(context).apply {
                    background = GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        setStroke(
                            strokeThickness,
                            ScanColorManager.getScanColorSetFromPreferences(context).primaryColor.toColorInt()
                        )
                    }
                }
                blockOutline?.let {
                    super.addView(
                        it,
                        bounds.left,
                        bounds.top,
                        bounds.width(),
                        bounds.height()
                    )
                }
            } else {
                updateBlockOutline()
            }
        } else {
            removeBlockOutline()
        }
    }

    private fun updateBlockOutline() {
        val bounds = getBounds()
        blockOutline?.let {
            super.updateView(it, bounds.left, bounds.top, bounds.width(), bounds.height())
        }
    }

    private fun removeBlockOutline() {
        removeBlockOutlineSafely()
    }

    private fun removeBlockOutlineSafely() {
        blockOutline?.let { outline ->
            try {
                if (outline.parent != null) {
                    super.removeView(outline)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                blockOutline = null
            }
        }
    }

    /**
     * Shows or updates the horizontal cursor line.
     *
     * @param x The x-coordinate for the line.
     */
    fun showXCursorLine(x: Int) {
        val bounds = getBounds()
        val yPosition = bounds.top
        val height = bounds.height()

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
        val bounds = getBounds()
        val xPosition = bounds.left
        val width = bounds.width()

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
        removeXCursorLineSafely()
    }

    /**
     * Removes the vertical cursor line.
     */
    fun removeYCursorLine() {
        removeYCursorLineSafely()
    }

    private fun removeXCursorLineSafely() {
        xCursorLine?.let { line ->
            try {
                if (line.parent != null) {
                    super.removeView(line)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                xCursorLine = null
            }
        }
    }

    private fun removeYCursorLineSafely() {
        yCursorLine?.let { line ->
            try {
                if (line.parent != null) {
                    super.removeView(line)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                yCursorLine = null
            }
        }
    }

    /**
     * Updates the position of the horizontal cursor line.
     *
     * @param x The new x-coordinate for the line.
     */
    private fun updateXCursorLine(x: Int) {
        val bounds = getBounds()
        val width = ScanMethodUIConstants.LINE_THICKNESS
        val height = bounds.height()
        xCursorLine?.let {
            super.updateView(it, x, bounds.top, width, height)
        }
    }

    /**
     * Updates the position of the vertical cursor line.
     *
     * @param y The new y-coordinate for the line.
     */
    private fun updateYCursorLine(y: Int) {
        val bounds = getBounds()
        val width = bounds.width()
        val height = ScanMethodUIConstants.LINE_THICKNESS
        yCursorLine?.let {
            super.updateView(it, bounds.left, y, width, height)
        }
    }

    /**
     * Removes all cursor UI elements.
     */
    fun reset() {
        try {
            removeXCursorLineSafely()
            removeYCursorLineSafely()
            removeBlockOutlineSafely()
            setBlock(null)
            super.hide()
        } catch (e: Exception) {
            // Force cleanup even if removal fails
            forceCleanup()
        }
    }

    private fun forceCleanup() {
        xCursorLine = null
        yCursorLine = null
        blockOutline = null
        currentBlock = null
        super.hide()
    }

    /**
     * Sets the current block for line dimensions.
     *
     * @param block The block to set, or null for screen dimensions.
     */
    fun setBlock(block: CursorBlock?) {
        currentBlock = block
        // Update existing lines if they're visible
        xCursorLine?.let { line ->
            val bounds = getBounds()
            super.updateView(
                line,
                line.x.toInt(),
                bounds.top,
                ScanMethodUIConstants.LINE_THICKNESS,
                bounds.height()
            )
        }
        yCursorLine?.let { line ->
            val bounds = getBounds()
            super.updateView(
                line,
                bounds.left,
                line.y.toInt(),
                bounds.width(),
                ScanMethodUIConstants.LINE_THICKNESS
            )
        }
        showBlockOutline()
    }
} 