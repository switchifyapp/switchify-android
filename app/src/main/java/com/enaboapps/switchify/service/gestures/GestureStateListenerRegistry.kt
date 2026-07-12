package com.enaboapps.switchify.service.gestures

import java.util.concurrent.ConcurrentHashMap

internal class GestureStateListenerRegistry {
    private val listeners = ConcurrentHashMap<String, GestureStateManager.GestureStateListener>()
    val size: Int
        get() = listeners.size

    fun add(id: String, listener: GestureStateManager.GestureStateListener) {
        listeners[id] = listener
    }

    fun remove(id: String) {
        listeners.remove(id)
    }

    fun clear() {
        listeners.clear()
    }

    fun notify(event: String, data: Map<String, Any>) {
        listeners.values.forEach { listener ->
            try {
                listener.onStateChanged(event, data)
            } catch (_: Exception) {
            }
        }
    }
}
