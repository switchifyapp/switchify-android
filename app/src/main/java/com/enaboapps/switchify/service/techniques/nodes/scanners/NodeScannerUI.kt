package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.os.Handler
import android.os.Looper
import android.view.animation.PathInterpolator
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.scanning.ScanHighlightDrawable
import com.enaboapps.switchify.service.scanning.ScanHighlightStyle
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

class NodeScannerUI {
    companion object {
        val instance: NodeScannerUI by lazy { NodeScannerUI() }

        private const val SHOW_DURATION_MS = 120L
        private const val HIDE_DURATION_MS = 80L
        private const val INITIAL_SCALE = 0.96f
    }

    private val window = SwitchifyAccessibilityWindow.instance

    private var style: ScanHighlightStyle? = null

    private var baseLayout: RelativeLayout? = null

    private var itemBoundsLayout: RelativeLayout? = null
    private var rowBoundsLayout: RelativeLayout? = null

    private val handler = Handler(Looper.getMainLooper())

    // Material 3 standard easing curve (fast out, slow in).
    private val showInterpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)

    private fun prepare() {
        if (baseLayout == null) {
            window.getContext()?.let { context ->
                baseLayout = RelativeLayout(context)
                baseLayout?.let { layout ->
                    window.addView(
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

    fun showItemBounds(x: Int, y: Int, width: Int, height: Int) {
        handler.post {
            prepare()
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
                        style,
                        ScanColorManager.getScanColorSetFromPreferences(it).secondaryColor
                    )
                }
                itemBoundsLayout = layout
                animateIn(layout)
                baseLayout?.addView(layout)
            }
        }
    }

    fun showRowBounds(x: Int, y: Int, width: Int, height: Int) {
        handler.post {
            prepare()
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
                        style,
                        ScanColorManager.getScanColorSetFromPreferences(it).primaryColor
                    )
                }
                rowBoundsLayout = layout
                animateIn(layout)
                baseLayout?.addView(layout)
            }
        }
    }

    private fun animateIn(layout: RelativeLayout) {
        layout.alpha = 0f
        layout.scaleX = INITIAL_SCALE
        layout.scaleY = INITIAL_SCALE
        layout.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(SHOW_DURATION_MS)
            .setInterpolator(showInterpolator)
            .start()
    }

    fun hideItemBounds() {
        handler.post {
            val view = itemBoundsLayout ?: return@post
            itemBoundsLayout = null
            view.animate()
                .alpha(0f)
                .setDuration(HIDE_DURATION_MS)
                .withEndAction { baseLayout?.removeView(view) }
                .start()
        }
    }

    fun hideRowBounds() {
        handler.post {
            val view = rowBoundsLayout ?: return@post
            rowBoundsLayout = null
            view.animate()
                .alpha(0f)
                .setDuration(HIDE_DURATION_MS)
                .withEndAction { baseLayout?.removeView(view) }
                .start()
        }
    }

    fun hideAll() {
        handler.post {
            itemBoundsLayout = null
            rowBoundsLayout = null
            val base = baseLayout ?: return@post
            baseLayout = null
            base.animate()
                .alpha(0f)
                .setDuration(HIDE_DURATION_MS)
                .withEndAction { window.removeView(base) }
                .start()
        }
    }
}
