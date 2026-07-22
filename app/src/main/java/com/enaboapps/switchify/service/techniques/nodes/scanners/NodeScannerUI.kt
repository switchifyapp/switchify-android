package com.enaboapps.switchify.service.techniques.nodes.scanners

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.scanning.ScanHighlightDrawable
import com.enaboapps.switchify.service.scanning.ScanHighlightStyle
import com.enaboapps.switchify.service.utils.HighlightAnimations
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import java.util.concurrent.atomic.AtomicLong

internal fun interface NodeScannerUiDispatcher {
    fun post(block: () -> Unit)
}

private object MainNodeScannerUiDispatcher : NodeScannerUiDispatcher {
    private val handler = Handler(Looper.getMainLooper())

    override fun post(block: () -> Unit) {
        handler.post(block)
    }
}

internal interface NodeScannerOverlayWindow {
    fun getContext(): Context?
    fun getDisplaySize(target: OverlayTarget): Pair<Int, Int>?
    fun addView(
        target: OverlayTarget,
        view: RelativeLayout,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    )
    fun removeView(target: OverlayTarget, view: RelativeLayout)
}

private object SwitchifyNodeScannerOverlayWindow : NodeScannerOverlayWindow {
    private val window = SwitchifyAccessibilityWindow.instance

    override fun getContext(): Context? = window.getContext()

    override fun getDisplaySize(target: OverlayTarget): Pair<Int, Int>? {
        val metrics = window.getDisplayMetrics(target) ?: return null
        return metrics.width to metrics.height
    }

    override fun addView(
        target: OverlayTarget,
        view: RelativeLayout,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        window.addView(target, view, x, y, width, height)
    }

    override fun removeView(target: OverlayTarget, view: RelativeLayout) {
        window.removeView(target, view)
    }
}

class NodeScannerUI internal constructor(
    private val window: NodeScannerOverlayWindow = SwitchifyNodeScannerOverlayWindow,
    private val dispatcher: NodeScannerUiDispatcher = MainNodeScannerUiDispatcher
) {
    companion object {
        val instance: NodeScannerUI by lazy { NodeScannerUI() }

        private val itemRoles = setOf(NodeScannerHighlightRole.ITEM)
        private val rowRoles = setOf(
            NodeScannerHighlightRole.ROW,
            NodeScannerHighlightRole.ESCAPE
        )
    }

    private data class ActiveHighlight(
        val state: NodeScannerHighlightState,
        val view: RelativeLayout
    )

    private val commandLock = Any()
    private val rendererEpoch = AtomicLong(0L)

    private var style: ScanHighlightStyle? = null
    private var baseLayout: RelativeLayout? = null
    private var activeHighlight: ActiveHighlight? = null
    private var overlayTarget: OverlayTarget = OverlayTargets.defaultDisplay()

    fun showItemBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget = OverlayTargets.defaultDisplay()
    ) {
        showHighlight(
            NodeScannerHighlightSpec(
                NodeScannerHighlightRole.ITEM,
                x,
                y,
                width,
                height,
                target
            )
        )
    }

    fun showRowBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget = OverlayTargets.defaultDisplay()
    ) {
        showHighlight(
            NodeScannerHighlightSpec(
                NodeScannerHighlightRole.ROW,
                x,
                y,
                width,
                height,
                target
            )
        )
    }

    fun showEscapeBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        target: OverlayTarget = OverlayTargets.defaultDisplay()
    ) {
        showHighlight(
            NodeScannerHighlightSpec(
                NodeScannerHighlightRole.ESCAPE,
                x,
                y,
                width,
                height,
                target
            )
        )
    }

    fun hideItemBounds() {
        submitCurrentEpoch {
            hideHighlight(itemRoles)
        }
    }

    fun hideRowBounds() {
        submitCurrentEpoch {
            hideHighlight(rowRoles)
        }
    }

    fun hideAll() {
        synchronized(commandLock) {
            val epoch = rendererEpoch.incrementAndGet()
            dispatcher.post {
                if (NodeScannerHighlightTransitions.isCurrentEpoch(epoch, rendererEpoch.get())) {
                    hideAllNow()
                }
            }
        }
    }

    private fun showHighlight(spec: NodeScannerHighlightSpec) {
        submitCurrentEpoch {
            render(spec)
        }
    }

    private fun submitCurrentEpoch(block: () -> Unit) {
        synchronized(commandLock) {
            val epoch = rendererEpoch.get()
            dispatcher.post {
                if (NodeScannerHighlightTransitions.isCurrentEpoch(epoch, rendererEpoch.get())) {
                    block()
                }
            }
        }
    }

    private fun render(spec: NodeScannerHighlightSpec) {
        when (
            NodeScannerHighlightTransitions.show(
                activeHighlight?.state,
                spec
            )
        ) {
            NodeScannerHighlightTransition.ATTACH -> attachHighlight(spec, animate = true)
            NodeScannerHighlightTransition.UPDATE -> updateHighlight(spec)
            NodeScannerHighlightTransition.REPLACE_TARGET -> {
                removeActiveHighlightNow()
                removeBaseLayoutNow()
                attachHighlight(spec, animate = false)
            }
            NodeScannerHighlightTransition.REMOVE,
            NodeScannerHighlightTransition.IGNORE -> Unit
        }
    }

    private fun updateHighlight(spec: NodeScannerHighlightSpec) {
        val active = activeHighlight ?: return
        normalize(active.view)
        applySpec(active.view, spec)
        activeHighlight = ActiveHighlight(
            NodeScannerHighlightState(spec.role, spec.target),
            active.view
        )
    }

    private fun attachHighlight(spec: NodeScannerHighlightSpec, animate: Boolean) {
        val context = window.getContext() ?: return
        val base = prepareBaseLayout(spec.target, context) ?: return
        val view = RelativeLayout(context)
        applySpec(view, spec)
        activeHighlight = ActiveHighlight(
            NodeScannerHighlightState(spec.role, spec.target),
            view
        )
        base.addView(view)
        if (animate) {
            HighlightAnimations.fadeIn(view)
        }
    }

    private fun applySpec(view: RelativeLayout, spec: NodeScannerHighlightSpec) {
        view.layoutParams = RelativeLayout.LayoutParams(spec.width, spec.height).apply {
            leftMargin = spec.x
            topMargin = spec.y
        }
        val context = window.getContext() ?: return
        val highlightStyle = style ?: ScanHighlightStyle(context).also { style = it }
        val colors = ScanColorManager.getScanColorSetFromPreferences(context)
        view.background = ScanHighlightDrawable(
            context,
            highlightStyle.isFill(),
            if (spec.role == NodeScannerHighlightRole.ITEM) {
                colors.secondaryColor
            } else {
                colors.primaryColor
            },
            isDashed = spec.role == NodeScannerHighlightRole.ESCAPE
        )
        view.requestLayout()
    }

    private fun prepareBaseLayout(
        target: OverlayTarget,
        context: Context
    ): RelativeLayout? {
        if (baseLayout != null && overlayTarget != target) {
            removeBaseLayoutNow()
        }
        overlayTarget = target
        if (baseLayout == null) {
            val base = RelativeLayout(context)
            val fallbackMetrics = context.resources.displayMetrics
            val displaySize = window.getDisplaySize(target)
            baseLayout = base
            window.addView(
                target,
                base,
                0,
                0,
                displaySize?.first ?: fallbackMetrics.widthPixels,
                displaySize?.second ?: fallbackMetrics.heightPixels
            )
        }
        return baseLayout
    }

    private fun hideHighlight(roles: Set<NodeScannerHighlightRole>) {
        if (
            NodeScannerHighlightTransitions.hide(
                activeHighlight?.state,
                roles
            ) == NodeScannerHighlightTransition.REMOVE
        ) {
            removeActiveHighlightNow()
        }
    }

    private fun removeActiveHighlightNow() {
        val active = activeHighlight ?: return
        activeHighlight = null
        active.view.animate().cancel()
        baseLayout?.removeView(active.view)
    }

    private fun removeBaseLayoutNow() {
        removeActiveHighlightNow()
        val base = baseLayout ?: return
        val target = overlayTarget
        baseLayout = null
        window.removeView(target, base)
    }

    private fun hideAllNow() {
        removeBaseLayoutNow()
    }

    private fun normalize(view: RelativeLayout) {
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }
}
