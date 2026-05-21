package com.enaboapps.switchify.service.menu

import android.content.Context
import android.widget.LinearLayout
import kotlin.math.min

/**
 * Stacks its children in a vertical list. Children are added MATCH_PARENT
 * width; the list caps its own width at [maxWidthPx] so a long suggestion row
 * wraps its text instead of stretching the menu surface across the screen, and
 * caps its height at [maxHeightPx] so the rows never push the nav row off the
 * bottom of the screen.
 */
class LinearMenuLayout(
    context: Context,
    private val maxWidthPx: Int = 0,
    private val maxHeightPx: Int = 0
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpec = if (maxWidthPx <= 0) {
            widthMeasureSpec
        } else {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val size = MeasureSpec.getSize(widthMeasureSpec)
            val capped = if (mode == MeasureSpec.UNSPECIFIED) maxWidthPx else min(size, maxWidthPx)
            MeasureSpec.makeMeasureSpec(capped, MeasureSpec.EXACTLY)
        }
        super.onMeasure(widthSpec, heightMeasureSpec)
        if (maxHeightPx in 1 until measuredHeight) {
            setMeasuredDimension(measuredWidth, maxHeightPx)
        }
    }
}
