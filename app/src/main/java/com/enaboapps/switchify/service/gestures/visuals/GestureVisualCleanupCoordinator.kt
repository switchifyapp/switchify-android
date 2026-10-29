package com.enaboapps.switchify.service.gestures.visuals

internal class GestureVisualCleanupCoordinator(
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val cleanup: () -> Unit,
    private val onCleanupFailed: (Throwable) -> Unit = {},
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES
) {
    private var completed = false
    private var running = false
    private var retries = 0
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
        if (completed || running) return
        running = true
        try {
            cleanup()
            completed = true
            watchdog?.let(removeCallbacks)
            watchdog = null
        } catch (throwable: Throwable) {
            onCleanupFailed(throwable)
            if (retries < maxRetries) {
                retries += 1
                schedule(retryDelayMs)
            } else {
                completed = true
                watchdog?.let(removeCallbacks)
                watchdog = null
            }
        } finally {
            running = false
        }
    }

    private companion object {
        const val DEFAULT_RETRY_DELAY_MS = 250L
        const val DEFAULT_MAX_RETRIES = 3
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
