package com.enaboapps.switchify.service.gestures.execution

import android.view.ViewConfiguration
import com.enaboapps.switchify.service.gestures.data.GestureData

object HoldAndDragTiming {
    const val RECOGNITION_BUFFER_MS = 100L

    fun holdDuration(longPressTimeoutMs: Long): Long =
        longPressTimeoutMs.coerceAtLeast(0L) + RECOGNITION_BUFFER_MS

    fun systemHoldDuration(): Long =
        holdDuration(ViewConfiguration.getLongPressTimeout().toLong())

    fun totalDuration(longPressTimeoutMs: Long): Long =
        holdDuration(longPressTimeoutMs) + GestureData.DRAG_DURATION
}
