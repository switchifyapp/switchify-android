package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.os.Handler
import android.os.Looper
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.scanning.ScanHighlightDrawable
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

class NodeScannerUI {
    companion object {
        val instance: NodeScannerUI by lazy { NodeScannerUI() }
    }

    private val window = SwitchifyAccessibilityWindow.instance

    private var baseLayout: RelativeLayout? = null

    private var itemBoundsLayout: RelativeLayout? = null
    private var rowBoundsLayout: RelativeLayout? = null

    private val handler = Handler(Looper.getMainLooper())

    private fun ensureBaseLayout() {
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
    }

    fun showItemBounds(x: Int, y: Int, width: Int, height: Int) {
        handler.post {
            ensureBaseLayout()
            window.getContext()?.let {
                val params = RelativeLayout.LayoutParams(
                    width,
                    height
                )
                params.leftMargin = x
                params.topMargin = y
                itemBoundsLayout = RelativeLayout(it).apply {
                    layoutParams = params
                }
                val border =
                    ScanHighlightDrawable(ScanColorManager.getScanColorSetFromPreferences(it).secondaryColor)
                itemBoundsLayout?.background = border
                itemBoundsLayout?.let { layout ->
                    baseLayout?.addView(layout)
                }
            }
        }
    }

    fun showRowBounds(x: Int, y: Int, width: Int, height: Int) {
        handler.post {
            ensureBaseLayout()
            window.getContext()?.let {
                val params = RelativeLayout.LayoutParams(
                    width,
                    height
                )
                params.leftMargin = x
                params.topMargin = y
                rowBoundsLayout = RelativeLayout(it).apply {
                    layoutParams = params
                }
                val border =
                    ScanHighlightDrawable(ScanColorManager.getScanColorSetFromPreferences(it).primaryColor)
                rowBoundsLayout?.background = border
                rowBoundsLayout?.let { layout ->
                    baseLayout?.addView(layout)
                }
            }
        }
    }

    fun hideItemBounds() {
        handler.post {
            itemBoundsLayout?.let {
                baseLayout?.removeView(it)
                itemBoundsLayout = null
            }
        }
    }

    fun hideRowBounds() {
        handler.post {
            rowBoundsLayout?.let {
                baseLayout?.removeView(it)
                rowBoundsLayout = null
            }
        }
    }

    fun hideAll() {
        hideItemBounds()
        hideRowBounds()
        baseLayout?.let {
            window.removeView(it)
            baseLayout = null
        }
    }
}