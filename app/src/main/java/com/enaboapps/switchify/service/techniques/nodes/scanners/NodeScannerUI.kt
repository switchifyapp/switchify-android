package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.os.Handler
import android.os.Looper
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.scanning.ScanHighlightDrawable
import com.enaboapps.switchify.service.scanning.ScanHighlightStyle
import com.enaboapps.switchify.service.utils.HighlightAnimations
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets

class NodeScannerUI {
    companion object {
        val instance: NodeScannerUI by lazy { NodeScannerUI() }
    }

    private val window = SwitchifyAccessibilityWindow.instance

    private var style: ScanHighlightStyle? = null

    private var baseLayout: RelativeLayout? = null

    private var itemBoundsLayout: RelativeLayout? = null
    private var rowBoundsLayout: RelativeLayout? = null
    private var overlayTarget: OverlayTarget = OverlayTargets.defaultDisplay()

    private val handler = Handler(Looper.getMainLooper())

    private fun prepare(target: OverlayTarget = OverlayTargets.defaultDisplay()) {
        overlayTarget = target
        if (baseLayout == null) {
            window.getContext()?.let { context ->
                baseLayout = RelativeLayout(context)
                baseLayout?.let { layout ->
                    window.addView(
                        OverlayTargets.displayFallback(target),
                        layout,
                        0,
                        0,
                        ScreenUtils.getWidth(context),
                        ScreenUtils.getHeight(context)
                    )
                }
            }
        }

        if (style == null) {
            window.getContext()?.let { context ->
                style = ScanHighlightStyle(context)
            }
        }
    }

    fun showItemBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget = OverlayTargets.defaultDisplay()
    ) {
        handler.post {
            prepare(target)
            window.getContext()?.let {
                val params = RelativeLayout.LayoutParams(
                    width,
                    height
                )
                params.leftMargin = x
                params.topMargin = y
                val layout = RelativeLayout(it).apply {
                    layoutParams = params
                }
                style?.let { style ->
                    layout.background = ScanHighlightDrawable(
                        it,
                        style.isFill(),
                        ScanColorManager.getScanColorSetFromPreferences(it).secondaryColor
                    )
                }
                itemBoundsLayout = layout
                HighlightAnimations.fadeIn(layout)
                baseLayout?.addView(layout)
            }
        }
    }

    fun showRowBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget = OverlayTargets.defaultDisplay()
    ) {
        handler.post {
            prepare(target)
            window.getContext()?.let {
                val params = RelativeLayout.LayoutParams(
                    width,
                    height
                )
                params.leftMargin = x
                params.topMargin = y
                val layout = RelativeLayout(it).apply {
                    layoutParams = params
                }
                style?.let { style ->
                    layout.background = ScanHighlightDrawable(
                        it,
                        style.isFill(),
                        ScanColorManager.getScanColorSetFromPreferences(it).primaryColor
                    )
                }
                rowBoundsLayout = layout
                HighlightAnimations.fadeIn(layout)
                baseLayout?.addView(layout)
            }
        }
    }

    fun showEscapeBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget = OverlayTargets.defaultDisplay()
    ) {
        handler.post {
            prepare(target)
            window.getContext()?.let {
                val params = RelativeLayout.LayoutParams(
                    width,
                    height
                )
                params.leftMargin = x
                params.topMargin = y
                val layout = RelativeLayout(it).apply {
                    layoutParams = params
                }
                style?.let { style ->
                    layout.background = ScanHighlightDrawable(
                        it,
                        style.isFill(),
                        ScanColorManager.getScanColorSetFromPreferences(it).primaryColor,
                        isDashed = true
                    )
                }
                rowBoundsLayout = layout
                HighlightAnimations.fadeIn(layout)
                baseLayout?.addView(layout)
            }
        }
    }

    fun hideItemBounds() {
        handler.post {
            val view = itemBoundsLayout ?: return@post
            itemBoundsLayout = null
            HighlightAnimations.fadeOut(view) { baseLayout?.removeView(view) }
        }
    }

    fun hideRowBounds() {
        handler.post {
            val view = rowBoundsLayout ?: return@post
            rowBoundsLayout = null
            HighlightAnimations.fadeOut(view) { baseLayout?.removeView(view) }
        }
    }

    fun hideAll() {
        handler.post {
            itemBoundsLayout = null
            rowBoundsLayout = null
            val base = baseLayout ?: return@post
            baseLayout = null
            val target = OverlayTargets.displayFallback(overlayTarget)
            HighlightAnimations.fadeOut(base) { window.removeView(target, base) }
        }
    }
}
