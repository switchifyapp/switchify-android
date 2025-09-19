package com.enaboapps.switchify.service.scanning

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.toColorInt

class ScanHighlightDrawable(
    highlightStyle: ScanHighlightStyle,
    color: String
) : LayerDrawable(createLayers(highlightStyle, color.toColorInt())) {
    companion object {
        const val FILL_ALPHA = 102
        const val CORNER_RADIUS = 16f
        const val MAIN_STROKE_WIDTH = 12
        const val OUTER_GLOW_WIDTH = 8
        const val INNER_HIGHLIGHT_WIDTH = 4
        const val OUTER_GLOW_OFFSET = 6
        const val INNER_HIGHLIGHT_INSET = 6

        private fun createLayers(highlightStyle: ScanHighlightStyle, colorAsInt: Int): Array<GradientDrawable> {
            return if (highlightStyle.isFill()) {
                createEnhancedFillLayers(colorAsInt)
            } else {
                createEnhancedBorderLayers(colorAsInt)
            }
        }

        private fun createEnhancedFillLayers(colorAsInt: Int): Array<GradientDrawable> {
            // Layer 1: Outer glow for depth
            val outerGlow = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CORNER_RADIUS + OUTER_GLOW_OFFSET
                setColor(Color.argb(80, 0, 0, 0)) // More visible shadow
            }

            // Layer 2: Main fill with subtle gradient
            val mainFill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CORNER_RADIUS
                val lightColor = Color.argb(FILL_ALPHA, Color.red(colorAsInt), Color.green(colorAsInt), Color.blue(colorAsInt))
                val darkColor = Color.argb(FILL_ALPHA - 20, Color.red(colorAsInt), Color.green(colorAsInt), Color.blue(colorAsInt))
                colors = intArrayOf(lightColor, darkColor)
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }

            // Layer 3: Inner highlight for premium look
            val innerHighlight = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CORNER_RADIUS - INNER_HIGHLIGHT_INSET
                setStroke(INNER_HIGHLIGHT_WIDTH, Color.argb(120, 255, 255, 255))
            }

            return arrayOf(outerGlow, mainFill, innerHighlight)
        }

        private fun createEnhancedBorderLayers(colorAsInt: Int): Array<GradientDrawable> {
            // Layer 1: Outer glow with color tint
            val outerGlow = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CORNER_RADIUS + OUTER_GLOW_OFFSET
                setStroke(OUTER_GLOW_WIDTH, Color.argb(80, Color.red(colorAsInt), Color.green(colorAsInt), Color.blue(colorAsInt)))
            }

            // Layer 2: Main border (preserved original behavior)
            val mainBorder = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CORNER_RADIUS
                setStroke(MAIN_STROKE_WIDTH, colorAsInt)
            }

            // Layer 3: Inner highlight with brighter accent
            val innerHighlight = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CORNER_RADIUS - INNER_HIGHLIGHT_INSET
                setStroke(
                    INNER_HIGHLIGHT_WIDTH,
                    Color.argb(
                        160,
                        Math.min(255, (Color.red(colorAsInt) * 1.4f).toInt()),
                        Math.min(255, (Color.green(colorAsInt) * 1.4f).toInt()),
                        Math.min(255, (Color.blue(colorAsInt) * 1.4f).toInt())
                    )
                )
            }

            return arrayOf(outerGlow, mainBorder, innerHighlight)
        }
    }

    init {
        // Position layers for sophisticated depth effect with more pronounced spacing
        setLayerInset(0, -OUTER_GLOW_OFFSET, -OUTER_GLOW_OFFSET, -OUTER_GLOW_OFFSET, -OUTER_GLOW_OFFSET)
        setLayerInset(1, 0, 0, 0, 0)
        setLayerInset(2, INNER_HIGHLIGHT_INSET, INNER_HIGHLIGHT_INSET, INNER_HIGHLIGHT_INSET, INNER_HIGHLIGHT_INSET)
    }
}