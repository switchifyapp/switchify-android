package com.enaboapps.switchify.pc.remote

import kotlinx.coroutines.CompletableDeferred

class PcSerialQueue {
    private val lock = Any()
    private var tail: CompletableDeferred<Unit>? = null

    suspend fun <T> enqueue(block: suspend () -> T): T {
        val done = CompletableDeferred<Unit>()
        val previous = synchronized(lock) { tail.also { tail = done } }
        try {
            previous?.await()
            return block()
        } finally {
            if (previous == null || previous.isCompleted) {
                done.complete(Unit)
            } else {
                previous.invokeOnCompletion { done.complete(Unit) }
            }
        }
    }

    suspend fun idle() {
        synchronized(lock) { tail }?.await()
    }
}
