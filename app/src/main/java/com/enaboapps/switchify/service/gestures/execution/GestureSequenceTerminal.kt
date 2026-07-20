package com.enaboapps.switchify.service.gestures.execution

import java.util.concurrent.atomic.AtomicBoolean

internal class GestureSequenceTerminal(
    private val onCompleted: () -> Unit,
    private val onCancelled: () -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val finished = AtomicBoolean(false)

    fun complete(): Boolean = finish(onCompleted)

    fun cancel(): Boolean = finish(onCancelled)

    fun error(error: Throwable): Boolean = finish { onError(error) }

    fun isFinished(): Boolean = finished.get()

    private fun finish(action: () -> Unit): Boolean {
        if (!finished.compareAndSet(false, true)) return false
        action()
        return true
    }
}
