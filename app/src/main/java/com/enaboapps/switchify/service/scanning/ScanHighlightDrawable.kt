package com.enaboapps.switchify.service.scanning

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.utils.ScreenUtils

class ScanHighlightDrawable(
    context: Context,
    isFill: Boolean,
    color: String,
    withHalo: Boolean = true
) : LayerDrawable(createLayers(context, isFill, color.toColorInt(), withHalo)) {

    private val haloOffsetPx = ScreenUtils.dpToPx(context, HALO_OFFSET_DP)
    private val drewHalo: Boolean = !isFill && withHalo

    init {
        when {
            isFill -> {
                setLayerInset(0, 0, 0, 0, 0)
                setLayerInset(1, 0, 0, 0, 0)
            }
            drewHalo -> {
                setLayerInset(0, -haloOffsetPx, -haloOffsetPx, -haloOffsetPx, -haloOffsetPx)
                setLayerInset(1, 0, 0, 0, 0)
            }
            else -> {
                setLayerInset(0, 0, 0, 0, 0)
            }
        }
    }

    companion object {
        private const val STROKE_WIDTH_DP = 3
        private const val FILL_STROKE_WIDTH_DP = 2
        private const val CORNER_RADIUS_DP = 8f
        private const val HALO_WIDTH_DP = 1
        private const val HALO_OFFSET_DP = 2
        private const val FILL_ALPHA = 31
        private const val HALO_ALPHA = 140

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
            isFill: Boolean,
            colorAsInt: Int,
            withHalo: Boolean
        ): Array<GradientDrawable> {
            return when {
                isFill -> createFillLayers(context, colorAsInt)
                withHalo -> createBorderLayers(context, colorAsInt)
                else -> arrayOf(strokeLayer(context, colorAsInt, STROKE_WIDTH_DP))
            }
        }

        private fun strokeLayer(
            context: Context,
            colorAsInt: Int,
            strokeDp: Int
        ): GradientDrawable {
            val cornerRadius = ScreenUtils.dpToPxFloat(context, CORNER_RADIUS_DP)
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                setStroke(ScreenUtils.dpToPx(context, strokeDp), colorAsInt)
            }
        }

        private fun createBorderLayers(
            context: Context,
            colorAsInt: Int
        ): Array<GradientDrawable> {
            val cornerRadius = ScreenUtils.dpToPxFloat(context, CORNER_RADIUS_DP)
            val haloRadius = cornerRadius + ScreenUtils.dpToPxFloat(context, HALO_OFFSET_DP.toFloat())

            val halo = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = haloRadius
                setStroke(ScreenUtils.dpToPx(context, HALO_WIDTH_DP), haloColor(colorAsInt))
            }

            val mainStroke = strokeLayer(context, colorAsInt, STROKE_WIDTH_DP)

            return arrayOf(halo, mainStroke)
        }

        private fun createFillLayers(
            context: Context,
            colorAsInt: Int
        ): Array<GradientDrawable> {
            val cornerRadius = ScreenUtils.dpToPxFloat(context, CORNER_RADIUS_DP)
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

            val mainStroke = strokeLayer(context, colorAsInt, FILL_STROKE_WIDTH_DP)

            return arrayOf(fill, mainStroke)
        }
    }
}
