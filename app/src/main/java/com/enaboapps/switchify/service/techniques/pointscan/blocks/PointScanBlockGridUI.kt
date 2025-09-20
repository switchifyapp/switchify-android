package com.enaboapps.switchify.service.techniques.pointscan.blocks

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.RelativeLayout
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.utils.ScreenUtils

class PointScanBlockGridUI(private val context: Context) : AccessTechniqueUIBase() {
    private val preferenceManager = PreferenceManager(context)
    private var gridViews: List<RelativeLayout> = emptyList()
    private var screenOutline: RelativeLayout? = null

    companion object {
        // Modern design constants
        private const val GRID_STROKE_WIDTH = 3
        private const val GRID_CORNER_RADIUS = 60f
        private const val GRID_SHADOW_OFFSET = 2
        private const val SCREEN_STROKE_WIDTH = 6
        private const val SCREEN_CORNER_RADIUS = 12f
        private const val SCREEN_SHADOW_OFFSET = 4
    }

    /**
     * Creates a custom drawable for grid blocks with square outer and rounded inner design.
     */
    private fun getModernGridDrawable() = PointScanBlockDrawable()

    /**
     * Creates a modern layered drawable for screen outline with pronounced depth effects.
     */
    private fun createModernScreenOutlineDrawable(): LayerDrawable {
        // Main border with modern styling
        val mainLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = SCREEN_CORNER_RADIUS
            setColor(Color.argb(12, 255, 255, 255)) // Subtle white fill
            setStroke(SCREEN_STROKE_WIDTH, Color.argb(150, 0, 0, 0)) // Strong border
        }

        // Inner highlight for premium look
        val highlightLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = SCREEN_CORNER_RADIUS - 2
            setStroke(2, Color.argb(80, 255, 255, 255)) // Bright inner highlight
        }

        return LayerDrawable(arrayOf(mainLayer, highlightLayer)).apply {
            // Keep layers properly aligned
            setLayerInset(0, 0, 0, 0, 0)
            setLayerInset(1, 4, 4, 4, 4)
        }
    }

    fun showGrid() {
        // Prevent duplicate grids - cleanup first if already exists
        if (gridViews.isNotEmpty() || screenOutline != null) {
            hideGrid()
        }

        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)

        val gridSize = preferenceManager.getStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
            "4"
        ).toInt()

        val blockWidth = screenWidth / gridSize
        val blockHeight = screenHeight / gridSize
        val strokeWidth = 6 // Match the stroke width from drawable
        val overlap = strokeWidth + 2 // Full stroke width plus extra to eliminate gaps

        // Create modern grid blocks with layered design
        gridViews = List(gridSize * gridSize) { index ->
            val row = index / gridSize
            val column = index % gridSize

            // Adjust positioning to eliminate gaps
            val left = column * blockWidth - if (column > 0) overlap else 0
            val top = row * blockHeight - if (row > 0) overlap else 0
            val width = blockWidth + if (column > 0) overlap else 0
            val height = blockHeight + if (row > 0) overlap else 0

            RelativeLayout(context).apply {
                background = getModernGridDrawable()
            }.also { view ->
                super.addView(
                    view,
                    left,
                    top,
                    width,
                    height
                )
            }
        }

        // Add modern screen outline with enhanced depth
        screenOutline = RelativeLayout(context).apply {
            background = createModernScreenOutlineDrawable()
        }
        screenOutline?.let {
            super.addView(
                it,
                0,
                0,
                screenWidth,
                screenHeight
            )
        }
    }

    fun hideGrid() {
        removeGridViewsSafely()
        removeScreenOutlineSafely()
    }

    private fun removeGridViewsSafely() {
        gridViews.forEach { view ->
            try {
                if (view.parent != null) {
                    super.removeView(view)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        gridViews = emptyList()
    }

    private fun removeScreenOutlineSafely() {
        screenOutline?.let { outline ->
            try {
                if (outline.parent != null) {
                    super.removeView(outline)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                screenOutline = null
            }
        }
    }

    fun reset() {
        try {
            removeGridViewsSafely()
            removeScreenOutlineSafely()
            super.hide()
        } catch (e: Exception) {
            // Force cleanup even if removal fails
            forceCleanup()
        }
    }

    private fun forceCleanup() {
        gridViews = emptyList()
        screenOutline = null
        super.hide()
    }
} 
