package com.enaboapps.switchify.pc.bluetooth

import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal class PcBleResponseRouter {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()

    fun register(requestId: String): CompletableDeferred<String> {
        val response = CompletableDeferred<String>()
        check(pending.putIfAbsent(requestId, response) == null)
        return response
    }

    fun unregister(requestId: String, response: CompletableDeferred<String>) {
        pending.remove(requestId, response)
    }

    fun route(message: String): Boolean {
        val requestId = runCatching { JSONObject(message).optString("id") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return false
        val response = pending.remove(requestId) ?: return false
        return response.complete(message)
    }

    fun fail(error: Throwable) {
        pending.entries.toList().forEach { (requestId, response) ->
            if (pending.remove(requestId, response)) {
                response.completeExceptionally(error)
            }
        }
    }
}
