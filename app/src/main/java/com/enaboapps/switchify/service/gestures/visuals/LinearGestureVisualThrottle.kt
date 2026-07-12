package com.enaboapps.switchify.service.gestures.visuals

internal class LinearGestureVisualThrottle<T>(
    private val intervalMs: Long,
    private val currentTimeMs: () -> Long,
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val display: (T) -> Unit
) {
    private var lastDisplayTimeMs: Long? = null
    private var pendingRequest: T? = null
    private var pendingRunnable: Runnable? = null

    fun submit(request: T) {
        val now = currentTimeMs()
        val lastDisplay = lastDisplayTimeMs
        if (lastDisplay == null || now - lastDisplay >= intervalMs) {
            cancelPending()
            lastDisplayTimeMs = now
            display(request)
            return
        }

        pendingRequest = request
        if (pendingRunnable != null) return

        val runnable = Runnable {
            pendingRunnable = null
            val latestRequest = pendingRequest ?: return@Runnable
            pendingRequest = null
            lastDisplayTimeMs = currentTimeMs()
            display(latestRequest)
        }
        pendingRunnable = runnable
        postDelayed(runnable, intervalMs - (now - lastDisplay))
    }

    fun clear() {
        cancelPending()
        lastDisplayTimeMs = null
    }

    private fun cancelPending() {
        pendingRunnable?.let(removeCallbacks)
        pendingRunnable = null
        pendingRequest = null
    }
}
