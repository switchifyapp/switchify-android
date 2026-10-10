package com.enaboapps.switchify.pc.connection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PcPermissionRequester(private val hasPermission: () -> Boolean) {
    private val lock = Any()
    private var hosts = 0
    private var pending: CompletableDeferred<Boolean>? = null
    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    suspend fun request(): Boolean {
        if (hasPermission()) return true
        val result = synchronized(lock) {
            if (hosts == 0) return false
            pending ?: CompletableDeferred<Boolean>().also {
                pending = it
                _requested.value = true
            }
        }
        return result.await() && hasPermission()
    }

    fun attachHost() {
        synchronized(lock) { hosts += 1 }
    }

    fun detachHost() {
        val abandoned = synchronized(lock) {
            hosts = maxOf(0, hosts - 1)
            if (hosts == 0) takePending() else null
        }
        abandoned?.complete(false)
    }

    fun complete(granted: Boolean) {
        synchronized(lock) { takePending() }?.complete(granted)
    }

    private fun takePending(): CompletableDeferred<Boolean>? {
        val current = pending
        pending = null
        _requested.value = false
        return current
    }
}
