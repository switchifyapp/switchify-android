package com.enaboapps.switchify.service.remotebridge

import android.os.Bundle
import android.os.RemoteCallbackList
import com.enaboapps.switchify.remotebridge.ISwitchifyRemoteBridgeCallback
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.switches.SwitchEventProvider

object SwitchifyRemoteBridgeCoordinator {
    const val VERSION = 1
    private val callbackDispatcher = SerializedCallbackDispatcher()
    private val callbacks = object : RemoteCallbackList<ISwitchifyRemoteBridgeCallback>() {
        override fun onCallbackDied(callback: ISwitchifyRemoteBridgeCallback?) {
            synchronized(lock) {
                callbackCount = (callbackCount - 1).coerceAtLeast(0)
                if (callbackCount == 0) clearActiveLocked()
            }
        }
    }
    private var callbackCount = 0
    private val lock = Any()
    private var externalSwitches: (() -> List<Pair<Int, String>>)? = null
    private var configuredSwitchFingerprint = emptyList<Pair<Int, String>>()
    private var repeatGeneration = 0L
    private var repeatGenerationHighWater = 0L
    private var forwardingGeneration = 0L
    private var forwardingGenerationHighWater = 0L
    private var edgeSequence = 0L
    private val activePresses = mutableMapOf<Int, Long>()

    fun attach(provider: SwitchEventProvider) = attach { provider.externalSwitches().mapNotNull { event -> event.code.toIntOrNull()?.let { it to event.name } } }
    internal fun attach(provider: () -> List<Pair<Int, String>>) = callbackDispatcher.dispatch {
        synchronized(lock) {
            externalSwitches = provider
            configuredSwitchFingerprint = configuredSwitchFingerprintLocked()
        }
        publishSnapshot()
    }
    fun detach() = callbackDispatcher.dispatch {
        synchronized(lock) {
            externalSwitches = null
            configuredSwitchFingerprint = emptyList()
            clearActiveLocked()
        }
        publishSnapshot()
    }
    fun register(callback: ISwitchifyRemoteBridgeCallback) = callbackDispatcher.dispatch {
        val value = synchronized(lock) {
            if (callbacks.register(callback)) callbackCount += 1
            snapshotLocked()
        }
        runCatching { callback.onSnapshot(value) }
        Unit
    }
    fun unregister(callback: ISwitchifyRemoteBridgeCallback) = callbackDispatcher.dispatch {
        synchronized(lock) {
            if (callbacks.unregister(callback)) callbackCount = (callbackCount - 1).coerceAtLeast(0)
            if (callbackCount == 0) clearActiveLocked()
        }
    }

    fun setRepeatActive(generation: Long, active: Boolean): Boolean = synchronized(lock) {
        if (generation <= 0) return false
        if (active) {
            if (generation <= repeatGenerationHighWater) return false
            repeatGenerationHighWater = generation
            repeatGeneration = generation
        } else {
            if (generation != repeatGeneration) return false
            repeatGeneration = 0
        }
        true
    }

    fun setForwardingActive(generation: Long, active: Boolean): Boolean = synchronized(lock) {
        if (externalSwitches == null || generation <= 0) return false
        if (active) {
            if (generation <= forwardingGenerationHighWater) return false
            forwardingGenerationHighWater = generation
            forwardingGeneration = generation
            edgeSequence = 0
            activePresses.clear()
            ServiceCore.getScanningManager()?.pauseScanning()
        } else {
            if (generation != forwardingGeneration) return false
            forwardingGeneration = 0
            activePresses.clear()
            ServiceCore.getScanningManager()?.resumeScanning()
        }
        true
    }

    fun stopRemoteRepeatForExternalSwitch(keyCode: Int): Boolean {
        if (!isConfiguredExternalSwitch(keyCode)) return false
        return stopRemoteRepeatForSwitch()
    }

    fun configuredSwitchesChanged() = callbackDispatcher.dispatch {
        synchronized(lock) {
            val nextFingerprint = configuredSwitchFingerprintLocked()
            val changed = nextFingerprint != configuredSwitchFingerprint
            configuredSwitchFingerprint = nextFingerprint
            if (changed && forwardingGeneration != 0L) {
                forwardingGeneration = 0
                edgeSequence = 0
                activePresses.clear()
                ServiceCore.getScanningManager()?.resumeScanning()
            }
        }
        publishSnapshot()
    }

    fun stopRemoteRepeatForSwitch(): Boolean = callbackDispatcher.dispatch {
        val generation = synchronized(lock) { repeatGeneration.also { repeatGeneration = 0 } }
        if (generation == 0L) return@dispatch false
        broadcast { it.onRepeatStopRequested(generation) }
        true
    }

    fun forwardExternalEdge(keyCode: Int, down: Boolean, downTimeMs: Long, eventTimeMs: Long, cancelled: Boolean): Boolean = callbackDispatcher.dispatch {
        val event = synchronized(lock) {
            val generation = forwardingGeneration
            if (generation == 0L || externalSwitches?.invoke()?.none { it.first == keyCode } != false) return@dispatch false
            if (down) {
                if (activePresses[keyCode] == downTimeMs) return@dispatch true
                activePresses[keyCode] = downTimeMs
            } else {
                if (activePresses[keyCode] != downTimeMs) return@dispatch true
                activePresses.remove(keyCode)
            }
            edgeSequence += 1
            Edge(generation, edgeSequence, keyCode, down, downTimeMs, eventTimeMs, cancelled)
        }
        broadcast { it.onSwitchEdge(event.generation, event.sequence, event.keyCode, event.down, event.downTimeMs, event.eventTimeMs, event.cancelled) }
        true
    }

    fun snapshot(): Bundle = synchronized(lock) { snapshotLocked() }

    private fun snapshotLocked() =
        Bundle().apply {
            putInt("version", VERSION)
            putBoolean("captureAvailable", externalSwitches != null)
            putParcelableArrayList("externalSwitches", ArrayList(externalSwitches?.invoke().orEmpty().map { event -> Bundle().apply { putInt("keyCode", event.first); putString("name", event.second) } }))
        }

    fun clearActive() = synchronized(lock) { clearActiveLocked() }
    internal fun activeForwardingGeneration() = synchronized(lock) { forwardingGeneration }
    internal fun currentEdgeSequence() = synchronized(lock) { edgeSequence }
    internal fun resetForTests() = synchronized(lock) {
        clearActiveLocked()
        repeatGenerationHighWater = 0
        forwardingGenerationHighWater = 0
    }
    private fun isConfiguredExternalSwitch(keyCode: Int) = synchronized(lock) {
        externalSwitches?.invoke()?.any { it.first == keyCode } == true
    }
    private fun configuredSwitchFingerprintLocked() = externalSwitches?.invoke().orEmpty()
        .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second })
        .take(8)
    private fun clearActiveLocked() { repeatGeneration = 0; forwardingGeneration = 0; edgeSequence = 0; activePresses.clear(); ServiceCore.getScanningManager()?.resumeScanning() }
    private fun publishSnapshot() {
        if (synchronized(lock) { callbackCount == 0 }) return
        val value = snapshot()
        broadcast { it.onSnapshot(value) }
    }
    private inline fun broadcast(crossinline block: (ISwitchifyRemoteBridgeCallback) -> Unit) = callbackDispatcher.dispatch {
        if (synchronized(lock) { callbackCount == 0 }) return@dispatch
        val count = callbacks.beginBroadcast()
        try { for (index in 0 until count) runCatching { block(callbacks.getBroadcastItem(index)) } } finally { callbacks.finishBroadcast() }
    }
    private data class Edge(val generation: Long, val sequence: Long, val keyCode: Int, val down: Boolean, val downTimeMs: Long, val eventTimeMs: Long, val cancelled: Boolean)
}

internal class SerializedCallbackDispatcher {
    private val lock = Any()

    fun <T> dispatch(block: () -> T): T = synchronized(lock) { block() }
}
