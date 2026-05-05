package com.enaboapps.switchify.service.techniques.pointscan.blocks

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.widget.RelativeLayout
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.utils.HighlightAnimations
import com.enaboapps.switchify.service.utils.ScreenUtils

class PointScanBlockGridUI(private val context: Context) : AccessTechniqueUIBase() {
    private val preferenceManager = PreferenceManager(context)
    private val handler = Handler(Looper.getMainLooper())
    private var gridViews: List<RelativeLayout> = emptyList()
    private var screenOutline: RelativeLayout? = null

    companion object {
        private const val GRID_STROKE_DP = 2
        private const val GRID_CORNER_RADIUS_DP = 8f
        private const val OVERLAP_PADDING_DP = 2

        // Structural tone for grid divisions and screen outline. Hardcoded so
        // it is always distinguishable from the user's selected scan colours
        // (which drive the active block highlight and cursor crosshair).
        private val GRID_COLOR = Color.argb(160, 0, 0, 0)
    }

    private fun structuralOutline(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ScreenUtils.dpToPxFloat(context, GRID_CORNER_RADIUS_DP)
            setStroke(ScreenUtils.dpToPx(context, GRID_STROKE_DP), GRID_COLOR)
        }
    }

    fun showGrid() {
        handler.post {
            if (gridViews.isNotEmpty() || screenOutline != null) {
                removeGridViewsSafely()
                removeScreenOutlineSafely()
            }

            val screenWidth = ScreenUtils.getWidth(context)
            val screenHeight = ScreenUtils.getHeight(context)

            val gridSize = preferenceManager.getStringValue(
                PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
                "4"
            ).toInt()

            val blockWidth = screenWidth / gridSize
            val blockHeight = screenHeight / gridSize
            val strokePx = ScreenUtils.dpToPx(context, GRID_STROKE_DP)
            val overlap = strokePx + ScreenUtils.dpToPx(context, OVERLAP_PADDING_DP)

            val newGridViews = mutableListOf<RelativeLayout>()

            for (index in 0 until gridSize * gridSize) {
                val row = index / gridSize
                val column = index % gridSize

                val left = column * blockWidth - if (column > 0) overlap else 0
                val top = row * blockHeight - if (row > 0) overlap else 0
                val width = blockWidth + if (column > 0) overlap else 0
                val height = blockHeight + if (row > 0) overlap else 0

                val view = RelativeLayout(context).apply {
                    background = structuralOutline()
                }

                addViewDirectly(view, left, top, width, height)
                HighlightAnimations.fadeIn(view)
                newGridViews.add(view)
            }

            val newScreenOutline = RelativeLayout(context).apply {
                background = structuralOutline()
            }
            addViewDirectly(newScreenOutline, 0, 0, screenWidth, screenHeight)
            HighlightAnimations.fadeIn(newScreenOutline)

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
            }
        }
        screenOutline = null
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
