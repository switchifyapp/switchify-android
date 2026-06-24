package com.enaboapps.switchify.service.window.overlay

import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.enaboapps.switchify.service.utils.ScreenUtils

object OverlayDisplayMetricsProvider {
    fun defaultDisplayMetrics(context: Context): OverlayDisplayMetrics {
        return displayMetrics(context, OverlayTargets.DEFAULT_DISPLAY_ID)
    }

    fun displayMetrics(
        context: Context,
        displayId: Int
    ): OverlayDisplayMetrics {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = context.getSystemService(WindowManager::class.java)?.currentWindowMetrics
            if (windowMetrics != null) {
                return OverlayDisplayMetrics(
                    displayId = displayId,
                    width = windowMetrics.bounds.width(),
                    height = windowMetrics.bounds.height()
                )
            }
        }
        return OverlayDisplayMetrics(
            displayId = displayId,
            width = ScreenUtils.getWidth(context),
            height = ScreenUtils.getHeight(context)
        )
    }
}
