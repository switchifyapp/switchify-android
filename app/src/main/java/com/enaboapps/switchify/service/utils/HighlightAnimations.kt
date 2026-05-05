package com.enaboapps.switchify.service.utils

import android.view.View
import android.view.animation.PathInterpolator

/**
 * Shared fade + scale animation timings for scan highlight surfaces.
 * Keeps item scan, point scan, and any future overlay visually consistent.
 */
object HighlightAnimations {
    const val SHOW_DURATION_MS = 120L
    const val HIDE_DURATION_MS = 80L
    private const val INITIAL_SCALE = 0.96f

    // Material 3 standard easing curve (fast out, slow in).
    private val showInterpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)

    fun fadeIn(view: View) {
        view.alpha = 0f
        view.scaleX = INITIAL_SCALE
        view.scaleY = INITIAL_SCALE
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(SHOW_DURATION_MS)
            .setInterpolator(showInterpolator)
            .start()
    }

    fun fadeOut(view: View, onEnd: () -> Unit) {
        view.animate()
            .alpha(0f)
            .setDuration(HIDE_DURATION_MS)
            .withEndAction(onEnd)
            .start()
    }
}
