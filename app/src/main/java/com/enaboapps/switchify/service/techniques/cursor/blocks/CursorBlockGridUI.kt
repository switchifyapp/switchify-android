package com.enaboapps.switchify.service.techniques.cursor.blocks

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.RelativeLayout
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.utils.ScreenUtils

class CursorBlockGridUI(private val context: Context) : AccessTechniqueUIBase() {
    private val preferenceManager = PreferenceManager(context)
    private var gridViews: List<RelativeLayout> = emptyList()
    private var screenOutline: RelativeLayout? = null

    fun showGrid() {
        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)

        val gridSize = preferenceManager.getStringValue(
            PreferenceManager.Keys.PREFERENCE_KEY_CURSOR_BLOCK_COUNT,
            "4"
        ).toInt()

        val color = Color.BLACK

        val blockWidth = screenWidth / gridSize
        val blockHeight = screenHeight / gridSize

        // Create grid lines
        gridViews = List(gridSize * gridSize) { index ->
            val row = index / gridSize
            val column = index % gridSize

            val left = column * blockWidth
            val top = row * blockHeight

            RelativeLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(4, color)
                }
            }.also { view ->
                super.addView(
                    view,
                    left,
                    top,
                    blockWidth,
                    blockHeight
                )
            }
        }

        // Add screen outline
        screenOutline = RelativeLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(8, color)
            }
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