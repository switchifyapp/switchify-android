package com.enaboapps.switchify.pc.transport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class PcReadResponsePoller(
    private val scope: CoroutineScope,
    private val read: suspend () -> ByteArray?,
    private val cancelRead: suspend () -> Unit,
    private val onFrame: (ByteArray) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val timeoutMs: Long
) {
    val ready = CompletableDeferred<Unit>()

    @Volatile
    private var active = true
    private val job = scope.launch(start = CoroutineStart.LAZY) { poll() }

    init {
        job.start()
    }

    fun stop() {
        if (!active) return
        active = false
        job.cancel()
        ready.completeExceptionally(PcBluetoothException("Bluetooth response reading stopped."))
        scope.launch {
            try {
                cancelRead()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun poll() {
        while (active) {
            val value = try {
                withTimeout(timeoutMs) { read() }
            } catch (_: TimeoutCancellationException) {
                fail()
                return
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                fail()
                return
            }
            if (!active) return
            if (value == null || value.size > MAX_READ_FRAME_BYTES) {
                fail()
                return
            }
            ready.complete(Unit)
            if (value.isNotEmpty()) onFrame(value)
            if (!active) return
            delay(if (value.isNotEmpty()) 0 else IDLE_POLL_DELAY_MS)
        }
    }

    private fun fail() {
        if (!active) return
        stop()
        onError(PcBluetoothException("Bluetooth response reading failed. Reconnect to the PC."))
    }

    companion object {
        const val MAX_READ_FRAME_BYTES = 180
        const val IDLE_POLL_DELAY_MS = 100L
    }
}
