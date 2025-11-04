package com.enaboapps.switchify.service.techniques.pointscan.blocks

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.widget.RelativeLayout
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.utils.ScreenUtils

class PointScanBlockGridUI(private val context: Context) : AccessTechniqueUIBase() {
    private val preferenceManager = PreferenceManager(context)
    private val handler = Handler(Looper.getMainLooper())
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
        handler.post {
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
            val strokeWidth = 6
            val overlap = strokeWidth + 2

            val newGridViews = mutableListOf<RelativeLayout>()

            // Create and add all grid blocks atomically
            for (index in 0 until gridSize * gridSize) {
                val row = index / gridSize
                val column = index % gridSize

                val left = column * blockWidth - if (column > 0) overlap else 0
                val top = row * blockHeight - if (row > 0) overlap else 0
                val width = blockWidth + if (column > 0) overlap else 0
                val height = blockHeight + if (row > 0) overlap else 0

                val view = RelativeLayout(context).apply {
                    background = getModernGridDrawable()
                }

                addViewDirectly(view, left, top, width, height)
                newGridViews.add(view)
            }

            // Create and add screen outline
            val newScreenOutline = RelativeLayout(context).apply {
                background = createModernScreenOutlineDrawable()
            }
            addViewDirectly(newScreenOutline, 0, 0, screenWidth, screenHeight)

            // Assign only after all views successfully added
            gridViews = newGridViews
            screenOutline = newScreenOutline
        }
    }

    fun hideGrid() {
        handler.post {
            removeGridViewsSafely()
            removeScreenOutlineSafely()
        }
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
        handler.post {
            try {
                removeGridViewsSafely()
                removeScreenOutlineSafely()
                super.hide()
            } catch (e: Exception) {
                forceCleanup()
            }
        }
    }

    private fun forceCleanup() {
        gridViews = emptyList()
        screenOutline = null
        super.hide()
    }
} 
