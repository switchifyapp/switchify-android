package com.enaboapps.switchify.service.utils

import android.view.View
import com.enaboapps.switchify.service.scanning.ScanVisualConstants

/**
 * Shared fade + scale animation timings for scan highlight surfaces.
 * Keeps item scan, point scan, and any future overlay visually consistent.
 * Timings and easing live in [ScanVisualConstants].
 */
object HighlightAnimations {

    fun fadeIn(view: View) {
        view.alpha = 0f
        view.scaleX = ScanVisualConstants.INITIAL_SCALE
        view.scaleY = ScanVisualConstants.INITIAL_SCALE
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ScanVisualConstants.SHOW_DURATION_MS)
            .setInterpolator(ScanVisualConstants.SHOW_INTERPOLATOR)
            .start()
    }

    fun fadeOut(view: View, onEnd: () -> Unit) {
        view.animate()
            .alpha(0f)
            .setDuration(ScanVisualConstants.HIDE_DURATION_MS)
            .withEndAction(onEnd)
            .start()
    }
}
