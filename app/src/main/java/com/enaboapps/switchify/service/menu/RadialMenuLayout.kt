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
 * Positions its child views on a single ring around an optional centre child.
 *
 * Ring children lay out at angles θᵢ = -π/2 + 2π·i/n so the first child sits at
 * 12 o'clock and subsequent children step clockwise. The first child added via
 * [addView] when [centerIndex] points at it is treated as the centre anchor.
 * Children are measured with fixed width/height supplied through layout params
 * (in pixels); this ViewGroup reports a square bounding box sized so the ring
 * just fits inside with the centre child overlaid at the midpoint.
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
    var minChordGapPx: Int = 0,
    /** Max allowable bounding-box width in px; if 0 no clamp is applied. */
    var maxWidthPx: Int = 0
) : ViewGroup(context) {

    /** Index of the child that should sit in the centre (or -1 for no centre). */
    var centerIndex: Int = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Measure every child at its requested (explicit) width/height.
        var maxItemW = 0
        var maxItemH = 0
        val ringChildren = mutableListOf<View>()
        var centerChild: View? = null
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val lp = child.layoutParams
            val widthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
            val heightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
            child.measure(widthSpec, heightSpec)
            maxItemW = max(maxItemW, child.measuredWidth)
            maxItemH = max(maxItemH, child.measuredHeight)
            if (i == centerIndex) centerChild = child else ringChildren.add(child)
        }

        val n = ringChildren.size
        val ringItemExtent = max(maxItemW, maxItemH)
        // Chord between adjacent item centres on a ring of n items is 2·r·sin(π/n).
        // Demand chord ≥ itemExtent + gap so items don't overlap. Solve for r:
        //   r ≥ (itemExtent + gap) / (2·sin(π/n)).
        // Special-case n ≤ 1 (no ring) and n = 2 (items diametrically opposed).
        var radius = when {
            n == 0 -> 0
            n == 1 -> 0
            n == 2 -> ceil((ringItemExtent + minChordGapPx) / 2.0).toInt()
            else -> {
                val minChord = (ringItemExtent + minChordGapPx).toDouble()
                ceil(minChord / (2.0 * sin(Math.PI / n))).toInt()
            }
        }

        // Clamp so the ring fits inside maxWidthPx (if supplied) or the parent's
        // width spec when measured EXACTLY/AT_MOST.
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

        val boundingSize = if (n == 0) {
            // Just the centre (if present) — size to that child.
            max(centerChild?.measuredWidth ?: 0, centerChild?.measuredHeight ?: 0)
        } else {
            2 * radius + ringItemExtent
        }

        setMeasuredDimension(
            resolveSize(boundingSize, widthMeasureSpec),
            resolveSize(boundingSize, heightMeasureSpec)
        )

        // Stash for onLayout
        measuredRadius = radius
        measuredRingItemExtent = ringItemExtent
    }

    private var measuredRadius: Int = 0
    private var measuredRingItemExtent: Int = 0

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val boundingW = r - l
        val boundingH = b - t
        val centerX = boundingW / 2
        val centerY = boundingH / 2
        val radius = measuredRadius

        val ringChildren = mutableListOf<Pair<Int, View>>()
        var centerChild: View? = null
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (i == centerIndex) centerChild = child else ringChildren.add(i to child)
        }

        val n = ringChildren.size
        if (n > 0) {
            for ((orderIndex, pair) in ringChildren.withIndex()) {
                val (_, child) = pair
                val theta = -Math.PI / 2.0 + 2.0 * Math.PI * orderIndex / n
                val cx = centerX + (radius * cos(theta)).toInt()
                val cy = centerY + (radius * sin(theta)).toInt()
                val left = cx - child.measuredWidth / 2
                val top = cy - child.measuredHeight / 2
                child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
            }
        }

        centerChild?.let {
            val left = centerX - it.measuredWidth / 2
            val top = centerY - it.measuredHeight / 2
            it.layout(left, top, left + it.measuredWidth, top + it.measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }
}
