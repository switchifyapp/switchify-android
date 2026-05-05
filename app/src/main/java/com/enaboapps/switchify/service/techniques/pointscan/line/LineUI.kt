package com.enaboapps.switchify.service.techniques.pointscan.line

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.scanning.ScanVisualConstants
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.techniques.pointscan.blocks.PointScanBlock
import com.enaboapps.switchify.service.utils.HighlightAnimations
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * LineUI class handles the creation, updating, and removal of scan (crosshair) lines.
 *
 * @property context The application context.
 */
class LineUI(private val context: Context) : AccessTechniqueUIBase() {
    // Scan lines
    private var xScanLine: RelativeLayout? = null
    private var yScanLine: RelativeLayout? = null
    private var blockOutline: RelativeLayout? = null
    private var currentBlock: PointScanBlock? = null

    private val cursorLinePx =
        ScreenUtils.dpToPx(context, ScanVisualConstants.CURSOR_LINE_DP)

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
                blockOutline = RelativeLayout(context).apply {
                    background = GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        setStroke(
                            cursorLinePx,
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
                    HighlightAnimations.fadeIn(it)
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
    fun showXScanLine(x: Int) {
        val bounds = getBounds()
        val yPosition = bounds.top
        val height = bounds.height()

        if (xScanLine == null) {
            xScanLine = RelativeLayout(context).apply {
                val color = ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor
                setBackgroundColor(color.toColorInt())
            }
            xScanLine?.let {
                super.addView(
                    it,
                    x,
                    yPosition,
                    cursorLinePx,
                    height
                )
                HighlightAnimations.fadeIn(it)
            }
        } else {
            updateXScanLine(x)
        }
    }

    /**
     * Shows or updates the vertical cursor line.
     *
     * @param y The y-coordinate for the line.
     */
    fun showYScanLine(y: Int) {
        val bounds = getBounds()
        val xPosition = bounds.left
        val width = bounds.width()

        if (yScanLine == null) {
            yScanLine = RelativeLayout(context).apply {
                val color = ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor
                setBackgroundColor(color.toColorInt())
            }
            yScanLine?.let {
                super.addView(
                    it,
                    xPosition,
                    y,
                    width,
                    cursorLinePx
                )
                HighlightAnimations.fadeIn(it)
            }
        } else {
            updateYScanLine(y)
        }
    }

    /**
     * Removes the horizontal cursor line.
     */
    fun removeXScanLine() {
        removeXScanLineSafely()
    }

    /**
     * Removes the vertical cursor line.
     */
    fun removeYScanLine() {
        removeYScanLineSafely()
    }

    private fun removeXScanLineSafely() {
        xScanLine?.let { line ->
            try {
                if (line.parent != null) {
                    super.removeView(line)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                xScanLine = null
            }
        }
    }

    private fun removeYScanLineSafely() {
        yScanLine?.let { line ->
            try {
                if (line.parent != null) {
                    super.removeView(line)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                yScanLine = null
            }
        }
    }

    /**
     * Updates the position of the horizontal cursor line.
     *
     * @param x The new x-coordinate for the line.
     */
    private fun updateXScanLine(x: Int) {
        val bounds = getBounds()
        val width = cursorLinePx
        val height = bounds.height()
        xScanLine?.let {
            super.updateView(it, x, bounds.top, width, height)
        }
    }

    /**
     * Updates the position of the vertical cursor line.
     *
     * @param y The new y-coordinate for the line.
     */
    private fun updateYScanLine(y: Int) {
        val bounds = getBounds()
        val width = bounds.width()
        val height = cursorLinePx
        yScanLine?.let {
            super.updateView(it, bounds.left, y, width, height)
        }
    }

    /**
     * Removes all cursor UI elements.
     */
    fun reset() {
        try {
            removeXScanLineSafely()
            removeYScanLineSafely()
            removeBlockOutlineSafely()
            setBlock(null)
            super.hide()
        } catch (e: Exception) {
            // Force cleanup even if removal fails
            forceCleanup()
        }
    }

    private fun forceCleanup() {
        xScanLine = null
        yScanLine = null
        blockOutline = null
        currentBlock = null
        super.hide()
    }

    /**
     * Sets the current block for line dimensions.
     *
     * @param block The block to set, or null for screen dimensions.
     */
    fun setBlock(block: PointScanBlock?) {
        currentBlock = block
        // Update existing lines if they're visible
        xScanLine?.let { line ->
            val bounds = getBounds()
            super.updateView(
                line,
                line.x.toInt(),
                bounds.top,
                cursorLinePx,
                bounds.height()
            )
        }
        yScanLine?.let { line ->
            val bounds = getBounds()
            super.updateView(
                line,
                bounds.left,
                line.y.toInt(),
                bounds.width(),
                cursorLinePx
            )
        }
        showBlockOutline()
    }
} 
