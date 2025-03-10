package com.enaboapps.switchify.service.scanning

import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.toColorInt

class ScanHighlightDrawable(
    highlightStyle: ScanHighlightStyle,
    color: String
) : GradientDrawable() {
    companion object {
        const val FILL_ALPHA = 102
        const val CORNER_RADIUS = 16f
        const val STROKE_WIDTH = 16
    }

    init {
        if (highlightStyle.isFill()) {
            createFill(color.toColorInt())
        } else {
            createBorder(color.toColorInt())
        }
    }

    private fun createFill(colorAsInt: Int) {
        // Set the color of the drawable with alpha of 40%
        setColor(colorAsInt)
        alpha = FILL_ALPHA
        cornerRadius = CORNER_RADIUS
    }

    private fun createBorder(colorAsInt: Int) {
        cornerRadius = CORNER_RADIUS
        setStroke(STROKE_WIDTH, colorAsInt)
    }
}