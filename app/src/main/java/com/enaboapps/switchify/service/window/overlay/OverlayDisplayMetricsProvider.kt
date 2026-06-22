package com.enaboapps.switchify.service.window.overlay

import android.content.Context
import com.enaboapps.switchify.service.utils.ScreenUtils

object OverlayDisplayMetricsProvider {
    fun defaultDisplayMetrics(context: Context): OverlayDisplayMetrics {
        return OverlayDisplayMetrics(
            displayId = OverlayTargets.DEFAULT_DISPLAY_ID,
            width = ScreenUtils.getWidth(context),
            height = ScreenUtils.getHeight(context)
        )
    }
}
