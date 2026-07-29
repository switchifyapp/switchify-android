package com.enaboapps.switchify.pc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

internal class PcDiscoveryLease(private val discovery: PcDiscovery) {
    private val lock = Any()
    private var requests = 0

    val isActive: Boolean
        get() = synchronized(lock) { requests > 0 }

    fun acquire() {
        val shouldStart = synchronized(lock) {
            requests++
            requests == 1
        }
        if (shouldStart) discovery.startDiscovery()
    }

    fun release() {
        val shouldStop = synchronized(lock) {
            if (requests == 0) return
            requests--
            requests == 0
        }
        if (shouldStop) discovery.stopDiscovery()
    }
}

internal class PcConnectionAttemptCoordinator {
    private val lock = Any()
    private var activeJob: Job? = null

    val isActive: Boolean
        get() = synchronized(lock) { activeJob != null }

    fun cancel(): Boolean {
        val cancelled = synchronized(lock) {
            activeJob.also { activeJob = null }
        } ?: return false
        cancelled.cancel()
        return true
    }

    suspend fun <T> runExclusive(block: suspend () -> T): T {
        val myJob = coroutineContext[Job]
        val previous = synchronized(lock) {
            activeJob.also { activeJob = myJob }
        }
        if (previous != null && previous !== myJob) previous.cancel()
        return try {
            block()
        } finally {
            synchronized(lock) {
                if (activeJob === myJob) activeJob = null
            }
        }
    }
}

internal class PcLiveConnectionJobs {
    private var eventsJob: Job? = null
    private var heartbeatJob: Job? = null
    private var uiPauseJob: Job? = null
    private var reconnectJob: Job? = null

    val isUiPausePending: Boolean
        get() = uiPauseJob?.isActive == true

    val isReconnectActive: Boolean
        get() = reconnectJob?.isActive == true

    fun observe(scope: CoroutineScope, block: suspend () -> Unit) {
        eventsJob?.cancel()
        eventsJob = scope.launch { block() }
    }

    fun startHeartbeat(scope: CoroutineScope, block: suspend () -> Unit) {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch { block() }
    }

    fun scheduleUiPause(scope: CoroutineScope, delayMs: Long, block: suspend () -> Unit) {
        if (uiPauseJob?.isActive == true) return
        uiPauseJob = scope.launch {
            delay(delayMs)
            block()
        }
    }

    fun cancelUiPause() {
        uiPauseJob?.cancel()
        uiPauseJob = null
    }

    fun startReconnect(scope: CoroutineScope, block: suspend () -> Unit) {
        if (reconnectJob?.isActive == true) return
        val job = scope.launch { block() }
        reconnectJob = job
        job.invokeOnCompletion {
            if (reconnectJob === job) reconnectJob = null
        }
    }

    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    fun closeConnection(connection: PcControlConnection?, reason: PcControlCloseReason) {
        eventsJob?.cancel()
        eventsJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        connection?.close(reason)
    }
}

internal object PcReconnectPolicy {
    private val backoffMs = listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)

    fun delayForFailure(failureCount: Int): Long {
        return backoffMs[failureCount.coerceIn(0, backoffMs.lastIndex)]
    }
}
