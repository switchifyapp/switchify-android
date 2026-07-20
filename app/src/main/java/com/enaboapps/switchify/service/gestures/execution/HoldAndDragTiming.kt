package com.enaboapps.switchify.service.gestures.execution

import android.view.ViewConfiguration
import com.enaboapps.switchify.service.gestures.data.GestureData

object HoldAndDragTiming {
    const val RECOGNITION_BUFFER_MS = 100L
    const val MINIMUM_HOLD_DURATION_MS = 1000L

    fun holdDuration(longPressTimeoutMs: Long): Long =
        (longPressTimeoutMs.coerceAtLeast(0L) + RECOGNITION_BUFFER_MS)
            .coerceAtLeast(MINIMUM_HOLD_DURATION_MS)

    fun systemHoldDuration(): Long =
        holdDuration(ViewConfiguration.getLongPressTimeout().toLong())

    fun totalDuration(longPressTimeoutMs: Long): Long =
        holdDuration(longPressTimeoutMs) + GestureData.DRAG_DURATION

    fun systemTotalDuration(): Long =
        totalDuration(ViewConfiguration.getLongPressTimeout().toLong())
}
