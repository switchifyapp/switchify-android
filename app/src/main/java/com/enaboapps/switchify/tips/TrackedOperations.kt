package com.enaboapps.switchify.tips

import java.util.concurrent.TimeoutException

class TrackedOperations<T : Any>(
    private val isComplete: (T) -> Boolean,
    private val await: (T, Long) -> Unit,
    private val onLateCompletion: (T, () -> Unit) -> Unit
) {
    private val tracked = mutableMapOf<String, T>()
    private val watched = mutableSetOf<T>()

    fun run(name: String, timeoutSeconds: Long, lateCompletion: () -> Unit, start: () -> T) {
        val operation = tracked[name] ?: start().also { tracked[name] = it }
        try {
            await(operation, timeoutSeconds)
        } catch (e: TimeoutException) {
            if (watched.add(operation)) onLateCompletion(operation, lateCompletion)
            throw TipOperationPendingException(name)
        } finally {
            if (isComplete(operation)) {
                tracked.remove(name, operation)
                watched.remove(operation)
            }
        }
    }

    fun settle(name: String, timeoutSeconds: Long, lateCompletion: () -> Unit) {
        val operation = tracked[name] ?: return
        try {
            run(name, timeoutSeconds, lateCompletion) { operation }
        } catch (e: TipOperationPendingException) {
            throw e
        } catch (e: Exception) {
            Unit
        }
    }

    fun forget(name: String) {
        tracked.remove(name)
    }
}
