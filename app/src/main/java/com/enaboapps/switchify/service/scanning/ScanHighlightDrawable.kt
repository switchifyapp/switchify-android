package com.enaboapps.switchify.service.scanning

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.toColorInt

class ScanHighlightDrawable(
    context: Context,
    private val highlightStyle: ScanHighlightStyle,
    color: String
) : LayerDrawable(createLayers(context, highlightStyle, color.toColorInt())) {

    private val haloOffsetPx =
        (HALO_OFFSET_DP * context.resources.displayMetrics.density).toInt()

    init {
        if (highlightStyle.isFill()) {
            setLayerInset(0, 0, 0, 0, 0)
            setLayerInset(1, 0, 0, 0, 0)
        } else {
            setLayerInset(0, -haloOffsetPx, -haloOffsetPx, -haloOffsetPx, -haloOffsetPx)
            setLayerInset(1, 0, 0, 0, 0)
        }
    }

    companion object {
        private const val STROKE_WIDTH_DP = 3
        private const val FILL_STROKE_WIDTH_DP = 2
        private const val CORNER_RADIUS_DP = 8f
        private const val HALO_WIDTH_DP = 1
        private const val HALO_OFFSET_DP = 2f
        private const val FILL_ALPHA = 31
        private const val HALO_ALPHA = 140

        private fun dp(context: Context, value: Float): Float =
            value * context.resources.displayMetrics.density

        private fun dpInt(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).toInt()

        // Pick a halo tone that contrasts with the main color so the highlight
        // remains visible when it overlaps a same-colored background.
        private fun haloColor(mainColor: Int): Int {
            val r = Color.red(mainColor)
            val g = Color.green(mainColor)
            val b = Color.blue(mainColor)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            return if (luminance > 0.5) {
                Color.argb(HALO_ALPHA, 0, 0, 0)
            } else {
                Color.argb(HALO_ALPHA, 255, 255, 255)
            }
        }

        private fun createLayers(
            context: Context,
            highlightStyle: ScanHighlightStyle,
            colorAsInt: Int
        ): Array<GradientDrawable> {
            return if (highlightStyle.isFill()) {
                createFillLayers(context, colorAsInt)
            } else {
                createBorderLayers(context, colorAsInt)
            }
        }

        private fun createBorderLayers(
            context: Context,
            colorAsInt: Int
        ): Array<GradientDrawable> {
            val cornerRadius = dp(context, CORNER_RADIUS_DP)
            val haloRadius = cornerRadius + dp(context, HALO_OFFSET_DP)

            val halo = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = haloRadius
                setStroke(dpInt(context, HALO_WIDTH_DP), haloColor(colorAsInt))
            }

            val mainStroke = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                setStroke(dpInt(context, STROKE_WIDTH_DP), colorAsInt)
            }

            return arrayOf(halo, mainStroke)
        }

        private fun createFillLayers(
            context: Context,
            colorAsInt: Int
        ): Array<GradientDrawable> {
            val cornerRadius = dp(context, CORNER_RADIUS_DP)
            val tint = Color.argb(
                FILL_ALPHA,
                Color.red(colorAsInt),
                Color.green(colorAsInt),
                Color.blue(colorAsInt)
            )

            val fill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                setColor(tint)
            }

            val mainStroke = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                setStroke(dpInt(context, FILL_STROKE_WIDTH_DP), colorAsInt)
            }

            return arrayOf(fill, mainStroke)
        }
    }
}
