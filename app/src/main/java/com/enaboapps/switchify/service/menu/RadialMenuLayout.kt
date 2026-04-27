package com.enaboapps.switchify.service.menu

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Positions its child views on a single ring.
 *
 * Children lay out at angles θᵢ = -π/2 + 2π·i/n so the first child sits at
 * 12 o'clock and subsequent children step clockwise. Children are measured
 * with fixed width/height from their layout params; this ViewGroup reports a
 * square bounding box sized so the ring just fits inside.
 *
 * Rings shrink to fit narrow parents: if the natural radius would push the
 * bounding box beyond [maxWidthPx], the radius is clamped so adjacent items
 * may overlap slightly — preferred to clipping on small devices.
 *
 * No custom layout params class — callers supply `ViewGroup.LayoutParams(w, h)`
 * with explicit pixel dimensions via [MenuItem.inflate].
 */
class RadialMenuLayout @JvmOverloads constructor(
    context: Context,
    /** Minimum gap (px) between adjacent ring items along the chord between their centres. */
    private val minChordGapPx: Int = 0,
    /** Max allowable bounding-box width in px; if 0 no clamp is applied. */
    private val maxWidthPx: Int = 0,
    /** Max allowable bounding-box height in px; if 0 no clamp is applied. */
    private val maxHeightPx: Int = 0
) : ViewGroup(context) {

    // Reusable scratch space so we don't allocate a list per measure/layout pass.
    private val ringChildren = ArrayList<View>()

    // Radius in px decided during onMeasure, consumed in onLayout.
    private var measuredRadius: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        ringChildren.clear()
        var maxItemW = 0
        var maxItemH = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val lp = child.layoutParams
            val widthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
            val heightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
            child.measure(widthSpec, heightSpec)
            maxItemW = max(maxItemW, child.measuredWidth)
            maxItemH = max(maxItemH, child.measuredHeight)
            ringChildren.add(child)
        }

        val n = ringChildren.size
        val ringItemExtent = max(maxItemW, maxItemH)
        // Chord between adjacent item centres on a ring of n items is 2·r·sin(π/n).
        // Demand chord ≥ itemExtent + gap so items don't overlap. Solve for r:
        //   r ≥ (itemExtent + gap) / (2·sin(π/n)).
        // Special-case n ≤ 1 (no ring) and n = 2 (items diametrically opposed).
        var radius = when {
            n <= 1 -> 0
            n == 2 -> ceil((ringItemExtent + minChordGapPx) / 2.0).toInt()
            else -> {
                val minChord = (ringItemExtent + minChordGapPx).toDouble()
                ceil(minChord / (2.0 * sin(Math.PI / n))).toInt()
            }
        }

        val parentWidthMode = MeasureSpec.getMode(widthMeasureSpec)
        val parentWidthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthCap = when {
            maxWidthPx > 0 && parentWidthMode != MeasureSpec.UNSPECIFIED ->
                min(maxWidthPx, parentWidthSize)
            maxWidthPx > 0 -> maxWidthPx
            parentWidthMode != MeasureSpec.UNSPECIFIED -> parentWidthSize
            else -> 0
        }
        if (widthCap > 0 && n > 0) {
            val maxRadius = (widthCap - ringItemExtent) / 2
            if (maxRadius > 0) radius = min(radius, maxRadius)
        }

        // Same clamp on the vertical axis. The ring's bounding box is square
        // (2·radius + itemExtent on both axes), so this only kicks in when the
        // height budget is tighter than the width budget — typically landscape
        // orientation at the largest user-selected Menu Size, where there is
        // plenty of horizontal room but the screen is short.
        val parentHeightMode = MeasureSpec.getMode(heightMeasureSpec)
        val parentHeightSize = MeasureSpec.getSize(heightMeasureSpec)
        val heightCap = when {
            maxHeightPx > 0 && parentHeightMode != MeasureSpec.UNSPECIFIED ->
                min(maxHeightPx, parentHeightSize)
            maxHeightPx > 0 -> maxHeightPx
            parentHeightMode != MeasureSpec.UNSPECIFIED -> parentHeightSize
            else -> 0
        }
        if (heightCap > 0 && n > 0) {
            val maxRadius = (heightCap - ringItemExtent) / 2
            if (maxRadius > 0) radius = min(radius, maxRadius)
        }

        val boundingSize = if (n == 0) 0 else 2 * radius + ringItemExtent
        setMeasuredDimension(
            resolveSize(boundingSize, widthMeasureSpec),
            resolveSize(boundingSize, heightMeasureSpec)
        )

        measuredRadius = radius
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val centerX = (r - l) / 2
        val centerY = (b - t) / 2
        val radius = measuredRadius
        val n = ringChildren.size
        if (n == 0) return

        for (i in 0 until n) {
            val child = ringChildren[i]
            val theta = -Math.PI / 2.0 + 2.0 * Math.PI * i / n
            val cx = centerX + (radius * cos(theta)).toInt()
            val cy = centerY + (radius * sin(theta)).toInt()
            val left = cx - child.measuredWidth / 2
            val top = cy - child.measuredHeight / 2
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }
}
