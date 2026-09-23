package com.enaboapps.switchify.service.techniques.pointscan.blocks

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualMotionPolicy
import com.enaboapps.switchify.service.scanning.ScanVisualConstants
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.techniques.pointscan.PointScanSettings
import com.enaboapps.switchify.service.utils.HighlightAnimations
import com.enaboapps.switchify.service.utils.ScreenUtils

class PointScanBlockGridUI(private val context: Context) : AccessTechniqueUIBase() {
    private val handler = Handler(Looper.getMainLooper())
    private var gridView: PointScanGridView? = null
    private val visibility = PointScanGridVisibility(
        post = { handler.post(it) },
        render = { phase ->
            when (phase) {
                PointScanGridPhase.HIDDEN -> removeGridNow()
                PointScanGridPhase.SCANNING -> showGridNow(1f)
                PointScanGridPhase.LINE -> showGridNow(ScanVisualConstants.GRID_DIMMED_ALPHA)
            }
        }
    )

    fun showGrid() = visibility.show()

    fun holdForLinePhase() = visibility.holdForLinePhase()

    fun hideGrid() = visibility.hide()

    fun reset() = visibility.reset()

    private fun showGridNow(alpha: Float) {
        val gridSize = PointScanSettings.getCursorBlockCount()
        val existing = gridView
        if (existing != null) {
            existing.gridSize = gridSize
            fadeTo(existing, alpha)
            return
        }
        val view = PointScanGridView(context).apply { this.gridSize = gridSize }
        addViewDirectly(view, 0, 0, ScreenUtils.getWidth(context), ScreenUtils.getHeight(context))
        gridView = view
        if (alpha == 1f && GestureVisualMotionPolicy.animationsEnabled()) {
            HighlightAnimations.fadeIn(view)
        } else {
            view.alpha = alpha
        }
    }

    private fun removeGridNow() {
        gridView?.animate()?.cancel()
        gridView = null
        hideDirectly()
    }

    private fun fadeTo(view: View, alpha: Float) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        if (!GestureVisualMotionPolicy.animationsEnabled()) {
            view.alpha = alpha
            return
        }
        view.animate()
            .alpha(alpha)
            .setDuration(ScanVisualConstants.SHOW_DURATION_MS)
            .setInterpolator(ScanVisualConstants.SHOW_INTERPOLATOR)
            .start()
    }
}
