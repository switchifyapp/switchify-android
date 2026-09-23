package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.scanning.ScanHighlightDrawable
import com.enaboapps.switchify.service.scanning.ScanInterval
import com.enaboapps.switchify.service.scanning.ScanVisualConstants
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws one scan highlight in screen coordinates: the optional spotlight dim,
 * the highlight drawable, and the auto-scan countdown ring.
 *
 * The view sizes itself: full-display while the spotlight is on (it has to
 * dim everything), otherwise just the highlight plus ring margin so the
 * per-frame countdown redraw only damages that small region. Geometry that
 * does not change between frames (cutout, ring outline, path length) is
 * rebuilt only when the bounds or spotlight flag change.
 */
internal class ScanHighlightView(
    context: Context,
    private val targetDisplayId: Int,
    private val onDisplayInvalidated: (ScanHighlightView) -> Unit
) : RelativeLayout(context) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private fun displayGeometry(): Triple<Int, Int, Int>? = displayManager?.getDisplay(targetDisplayId)?.let {
        Triple(it.rotation, it.mode.physicalWidth, it.mode.physicalHeight)
    }
    private var geometry = displayGeometry()
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == targetDisplayId) onDisplayInvalidated(this@ScanHighlightView)
        }
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == targetDisplayId && geometry != displayGeometry()) {
                onDisplayInvalidated(this@ScanHighlightView)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        geometry = displayGeometry()
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    override fun onDetachedFromWindow() {
        displayManager?.unregisterDisplayListener(displayListener)
        interval = null
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Only a geometry change moves the scanned bounds. Theme, locale, font
        // scale and keyboard changes must leave the highlight where it is.
        if (geometry != displayGeometry()) onDisplayInvalidated(this)
    }

    var highlightBounds = ScanHighlightBounds(0f, 0f, 0f, 0f)
        set(value) {
            if (field == value) return
            field = value
            geometryDirty = true
            syncFrame()
            invalidate()
        }
    var highlightDrawable: Drawable? = null
        set(value) {
            if (field === value) return
            field = value
            invalidate()
        }
    var spotlight = false
        set(value) {
            if (field == value) return
            field = value
            geometryDirty = true
            syncFrame()
            invalidate()
        }
    var interval: ScanInterval? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** The highlight's own colour; the ring picks contrasting tones from it. */
    var highlightColor = Color.WHITE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val ringInset = ScanVisualConstants.COUNTDOWN_INSET_DP * density
    private val ringStroke = ScanVisualConstants.COUNTDOWN_STROKE_DP * density
    private val ringHaloStroke = ScanVisualConstants.COUNTDOWN_HALO_STROKE_DP * density

    /** How far the ring and the drawable's halo may extend past the bounds. */
    private val frameMargin = ringInset + ringHaloStroke
    private val dimColor = Color.argb(ScanVisualConstants.SPOTLIGHT_ALPHA, 0, 0, 0)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cutout = Path()
    private val outline = Path()
    private val segment = Path()
    private val measure = PathMeasure()
    private val target = RectF()
    private val ring = RectF()
    private var outlineLength = 0f
    private var geometryDirty = true

    /** Screen-space position of this view's top-left corner. */
    private var originX = 0f
    private var originY = 0f

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }

    private fun syncFrame() {
        val bounds = highlightBounds
        if (spotlight || !bounds.isUsable) {
            originX = 0f
            originY = 0f
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            return
        }
        val left = floor(bounds.x - frameMargin).toInt()
        val top = floor(bounds.y - frameMargin).toInt()
        val right = ceil(bounds.x + bounds.width + frameMargin).toInt()
        val bottom = ceil(bounds.y + bounds.height + frameMargin).toInt()
        originX = left.toFloat()
        originY = top.toFloat()
        layoutParams = LayoutParams(right - left, bottom - top).apply {
            leftMargin = left
            topMargin = top
        }
    }

    private fun rebuildGeometry(bounds: ScanHighlightBounds) {
        target.set(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
        val radius = min(
            ScanVisualConstants.CORNER_RADIUS_DP * density,
            min(bounds.width, bounds.height) / 2f
        )
        cutout.reset()
        cutout.addRoundRect(target, radius, radius, Path.Direction.CW)
        ring.set(target)
        ring.inset(-ringInset, -ringInset)
        perimeter(ring, radius + ringInset)
        measure.setPath(outline, false)
        outlineLength = measure.length
        geometryDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bounds = highlightBounds
        if (!bounds.isUsable) return
        if (geometryDirty) rebuildGeometry(bounds)
        canvas.save()
        canvas.translate(-originX, -originY)
        if (spotlight) {
            canvas.save()
            canvas.clipOutPath(cutout)
            canvas.drawColor(dimColor)
            canvas.restore()
        }
        highlightDrawable?.let { drawable ->
            canvas.save()
            canvas.translate(bounds.x, bounds.y)
            drawable.setBounds(0, 0, bounds.width.roundToInt(), bounds.height.roundToInt())
            drawable.draw(canvas)
            canvas.restore()
        }
        drawCountdown(canvas)
        canvas.restore()
    }

    private fun drawCountdown(canvas: Canvas) {
        val timing = interval ?: return
        val remaining = timing.remainingFraction(SystemClock.uptimeMillis())
        if (remaining <= 0f || outlineLength <= 0f) return
        segment.reset()
        measure.getSegment(outlineLength * (1f - remaining), outlineLength, segment, true)
        val ringColor = ScanHighlightDrawable.contrastTone(highlightColor)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = ringHaloStroke
        paint.color = ScanHighlightDrawable.contrastTone(ringColor)
        canvas.drawPath(segment, paint)
        paint.strokeWidth = ringStroke
        paint.color = ringColor
        canvas.drawPath(segment, paint)
        postInvalidateOnAnimation()
    }

    /** Builds [outline] clockwise from the top centre so the ring drains from twelve o'clock. */
    private fun perimeter(rect: RectF, radius: Float) {
        val r = min(radius, min(rect.width(), rect.height()) / 2f)
        outline.reset()
        outline.moveTo(rect.centerX(), rect.top)
        outline.lineTo(rect.right - r, rect.top)
        outline.arcTo(rect.right - 2 * r, rect.top, rect.right, rect.top + 2 * r, -90f, 90f, false)
        outline.lineTo(rect.right, rect.bottom - r)
        outline.arcTo(rect.right - 2 * r, rect.bottom - 2 * r, rect.right, rect.bottom, 0f, 90f, false)
        outline.lineTo(rect.left + r, rect.bottom)
        outline.arcTo(rect.left, rect.bottom - 2 * r, rect.left + 2 * r, rect.bottom, 90f, 90f, false)
        outline.lineTo(rect.left, rect.top + r)
        outline.arcTo(rect.left, rect.top, rect.left + 2 * r, rect.top + 2 * r, 180f, 90f, false)
        outline.close()
    }
}
