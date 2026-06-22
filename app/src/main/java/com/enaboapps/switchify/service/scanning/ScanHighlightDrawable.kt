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
    isDashed: Boolean = false
) : LayerDrawable(createLayers(context, isFill, color.toColorInt(), isDashed)) {

    private val haloOffsetPx =
        ScreenUtils.dpToPx(context, ScanVisualConstants.HALO_OFFSET_DP)

    init {
        if (isFill) {
            setLayerInset(0, 0, 0, 0, 0)
            setLayerInset(1, 0, 0, 0, 0)
        } else {
            setLayerInset(0, -haloOffsetPx, -haloOffsetPx, -haloOffsetPx, -haloOffsetPx)
            setLayerInset(1, 0, 0, 0, 0)
        }
    }

    companion object {
        // Pick a halo tone that contrasts with the main color so the highlight
        // remains visible when it overlaps a same-colored background.
        private fun haloColor(mainColor: Int): Int {
            val r = Color.red(mainColor)
            val g = Color.green(mainColor)
            val b = Color.blue(mainColor)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            return if (luminance > 0.5) {
                Color.argb(ScanVisualConstants.HALO_ALPHA, 0, 0, 0)
            } else {
                Color.argb(ScanVisualConstants.HALO_ALPHA, 255, 255, 255)
            }
        }

        private fun createLayers(
            context: Context,
            isFill: Boolean,
            colorAsInt: Int,
            isDashed: Boolean
        ): Array<GradientDrawable> {
            return if (isFill) {
                createFillLayers(context, colorAsInt, isDashed)
            } else {
                createBorderLayers(context, colorAsInt, isDashed)
            }
        }

        private fun strokeLayer(
            context: Context,
            colorAsInt: Int,
            strokeDp: Int,
            isDashed: Boolean = false
        ): GradientDrawable {
            val cornerRadius =
                ScreenUtils.dpToPxFloat(context, ScanVisualConstants.CORNER_RADIUS_DP)
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                if (isDashed) {
                    val dashWidth = ScreenUtils.dpToPxFloat(
                        context,
                        ScanVisualConstants.ESCAPE_DASH_WIDTH_DP.toFloat()
                    )
                    val dashGap = ScreenUtils.dpToPxFloat(
                        context,
                        ScanVisualConstants.ESCAPE_DASH_GAP_DP.toFloat()
                    )
                    setStroke(
                        ScreenUtils.dpToPx(context, strokeDp),
                        colorAsInt,
                        dashWidth,
                        dashGap
                    )
                } else {
                    setStroke(ScreenUtils.dpToPx(context, strokeDp), colorAsInt)
                }
            }
        }

        private fun createBorderLayers(
            context: Context,
            colorAsInt: Int,
            isDashed: Boolean
        ): Array<GradientDrawable> {
            val cornerRadius =
                ScreenUtils.dpToPxFloat(context, ScanVisualConstants.CORNER_RADIUS_DP)
            val haloRadius = cornerRadius +
                ScreenUtils.dpToPxFloat(context, ScanVisualConstants.HALO_OFFSET_DP.toFloat())

            val halo = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = haloRadius
                setStroke(
                    ScreenUtils.dpToPx(context, ScanVisualConstants.HALO_STROKE_DP),
                    haloColor(colorAsInt)
                )
            }

            val mainStroke = strokeLayer(
                context,
                colorAsInt,
                ScanVisualConstants.ACTIVE_STROKE_DP,
                isDashed
            )

            return arrayOf(halo, mainStroke)
        }

        private fun createFillLayers(
            context: Context,
            colorAsInt: Int,
            isDashed: Boolean
        ): Array<GradientDrawable> {
            val cornerRadius =
                ScreenUtils.dpToPxFloat(context, ScanVisualConstants.CORNER_RADIUS_DP)
            val tint = Color.argb(
                ScanVisualConstants.FILL_ALPHA,
                Color.red(colorAsInt),
                Color.green(colorAsInt),
                Color.blue(colorAsInt)
            )

            val fill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                setColor(tint)
            }

            val mainStroke = strokeLayer(
                context,
                colorAsInt,
                ScanVisualConstants.FILL_STROKE_DP,
                isDashed
            )

            return arrayOf(fill, mainStroke)
        }
    }
}
