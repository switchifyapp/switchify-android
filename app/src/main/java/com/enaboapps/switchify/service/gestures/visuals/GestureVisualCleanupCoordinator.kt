package com.enaboapps.switchify.service.gestures.visuals

internal class GestureVisualCleanupCoordinator(
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val cleanup: () -> Unit
) {
    private var completed = false
    private var watchdog: Runnable? = null

    fun schedule(delayMs: Long) {
        if (completed) return
        watchdog?.let(removeCallbacks)
        lateinit var callback: Runnable
        callback = Runnable {
            if (watchdog === callback) complete()
        }
        watchdog = callback
        postDelayed(callback, delayMs.coerceAtLeast(0L))
    }

    fun complete() {
        if (completed) return
        completed = true
        watchdog?.let(removeCallbacks)
        watchdog = null
        cleanup()
    }
}

internal object GestureVisualCleanupDeadline {
    fun calculate(durationMs: Long, durationScale: Float, graceMs: Long): Long {
        val scaledDuration = durationMs.coerceAtLeast(0L).toDouble() *
            durationScale.coerceAtLeast(0f).toDouble()
        return (scaledDuration + graceMs.coerceAtLeast(0L))
            .coerceAtMost(Long.MAX_VALUE.toDouble())
            .toLong()
    }
}
