package com.enaboapps.switchify.pc.client

import com.enaboapps.switchify.pc.protocol.PcFrameReassembler
import com.enaboapps.switchify.pc.protocol.PcFraming
import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcReassemblyResult
import com.enaboapps.switchify.pc.protocol.PcResponse
import com.enaboapps.switchify.pc.protocol.PcResponses
import com.enaboapps.switchify.pc.transport.PcBluetoothException
import com.enaboapps.switchify.pc.transport.PcTransport
import com.enaboapps.switchify.pc.transport.PcUnsubscribe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

class PcProtocolWriteException : PcBluetoothException("Could not write to PC.")

class PcProtocolResponseTimeoutException : PcBluetoothException("PC response timed out.")

class PcProtocolClient(
    private val transport: PcTransport,
    private val scope: CoroutineScope,
    private val frameIds: PcIdGenerator = PcIdGenerator.Frame,
    private val writeTimeoutMs: Long = DEFAULT_WRITE_TIMEOUT_MS,
    now: () -> Long = System::currentTimeMillis
) {
    private val reassembler = PcFrameReassembler(now)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<PcResponse>>()
    private val writeCancels = ConcurrentHashMap.newKeySet<CompletableDeferred<Unit>>()
    private val writeMutex = Mutex()

    @Volatile
    private var unsubscribe: PcUnsubscribe? = null

    suspend fun start(onFailure: () -> Unit) {
        unsubscribe?.unsubscribe()
        unsubscribe = transport.subscribe(::accept) {
            onFailure()
            scope.launch { close() }
        }
        try {
            transport.notificationsReady()
        } catch (error: Throwable) {
            unsubscribe?.unsubscribe()
            unsubscribe = null
            throw error
        }
    }

    suspend fun request(message: String, requestId: String, timeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS): PcResponse {
        val response = CompletableDeferred<PcResponse>()
        pending[requestId] = response
        try {
            try {
                send(message)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                reject(requestId, PcProtocolWriteException())
            }
            return try {
                withTimeout(timeoutMs) { response.await() }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                throw PcProtocolResponseTimeoutException()
            }
        } finally {
            pending.remove(requestId, response)
        }
    }

    suspend fun send(message: String) {
        val frames = PcFraming.createFramesForWriteLimit(message, frameIds.nextId(), transport.maxWriteValueBytes())
        frames.forEach { frame ->
            writeMutex.withLock { write(PcFraming.encodeFrame(frame)) }
        }
    }

    suspend fun close() {
        unsubscribe?.unsubscribe()
        unsubscribe = null
        reassembler.clear()
        cancelOutstanding()
    }

    fun cancelPending() {
        pending.keys.toList().forEach { reject(it, PcBluetoothException(DISCONNECTED_MESSAGE)) }
    }

    suspend fun cancelOutstanding() {
        cancelPending()
        transport.cancelPendingWrites()
        writeCancels.toList().forEach { it.complete(Unit) }
    }

    private suspend fun write(frame: ByteArray) {
        val cancellation = CompletableDeferred<Unit>()
        writeCancels += cancellation
        try {
            withTimeout(writeTimeoutMs) {
                coroutineScope {
                    val canceller = launch {
                        cancellation.await()
                        throw PcBluetoothException(DISCONNECTED_MESSAGE)
                    }
                    try {
                        transport.writeFrame(frame)
                    } finally {
                        canceller.cancel()
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            transport.cancelPendingWrites()
            throw PcBluetoothException("Bluetooth write timed out.")
        } finally {
            writeCancels -= cancellation
        }
    }

    private fun accept(raw: ByteArray) {
        val frame = PcFraming.decodeFrame(raw) ?: return
        val result = reassembler.accept(frame) as? PcReassemblyResult.Complete ?: return
        val response = PcResponses.parseResponse(result.message)
        if (response is PcResponse.Invalid) return
        val id = response.responseId ?: return
        pending.remove(id)?.complete(response)
    }

    private fun reject(id: String, error: Throwable) {
        pending.remove(id)?.completeExceptionally(error)
    }

    companion object {
        const val DEFAULT_WRITE_TIMEOUT_MS = 5_000L
        const val DEFAULT_REQUEST_TIMEOUT_MS = 10_000L
        private const val DISCONNECTED_MESSAGE = "PC disconnected."
    }
}
