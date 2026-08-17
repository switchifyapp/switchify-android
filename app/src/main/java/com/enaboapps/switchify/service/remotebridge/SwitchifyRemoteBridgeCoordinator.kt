package com.enaboapps.switchify.service.remotebridge

import android.os.Bundle
import android.os.RemoteCallbackList
import com.enaboapps.switchify.remotebridge.ISwitchifyRemoteBridgeCallback
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.switches.SwitchEventProvider

object SwitchifyRemoteBridgeCoordinator {
    const val VERSION = 1
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
    private var repeatGeneration = 0L
    private var forwardingGeneration = 0L
    private var edgeSequence = 0L
    private val activePresses = mutableMapOf<Int, Long>()

    fun attach(provider: SwitchEventProvider) = attach { provider.externalSwitches().mapNotNull { event -> event.code.toIntOrNull()?.let { it to event.name } } }
    internal fun attach(provider: () -> List<Pair<Int, String>>) = synchronized(lock) { externalSwitches = provider; publishSnapshot() }
    fun detach() = synchronized(lock) { externalSwitches = null; clearActiveLocked(); publishSnapshot() }
    fun register(callback: ISwitchifyRemoteBridgeCallback) { synchronized(lock) { if (callbacks.register(callback)) callbackCount += 1 }; runCatching { callback.onSnapshot(snapshot()) } }
    fun unregister(callback: ISwitchifyRemoteBridgeCallback) { synchronized(lock) { if (callbacks.unregister(callback)) callbackCount = (callbackCount - 1).coerceAtLeast(0) }; clearIfUnbound() }

    fun setRepeatActive(generation: Long, active: Boolean): Boolean = synchronized(lock) {
        if (generation <= 0 || (active && generation < repeatGeneration)) return false
        if (active) repeatGeneration = generation else if (generation == repeatGeneration) repeatGeneration = 0
        true
    }

    fun setForwardingActive(generation: Long, active: Boolean): Boolean = synchronized(lock) {
        if (externalSwitches == null || generation <= 0 || (active && generation < forwardingGeneration)) return false
        if (active) { forwardingGeneration = generation; edgeSequence = 0; activePresses.clear(); ServiceCore.getScanningManager()?.pauseScanning() }
        else if (generation == forwardingGeneration) { forwardingGeneration = 0; activePresses.clear(); ServiceCore.getScanningManager()?.resumeScanning() }
        true
    }

    fun stopRemoteRepeatForSwitch(): Boolean {
        val generation = synchronized(lock) { repeatGeneration.also { repeatGeneration = 0 } }
        if (generation == 0L) return false
        broadcast { it.onRepeatStopRequested(generation) }
        return true
    }

    fun forwardExternalEdge(keyCode: Int, down: Boolean, downTimeMs: Long, eventTimeMs: Long, cancelled: Boolean): Boolean {
        val event = synchronized(lock) {
            val generation = forwardingGeneration
            if (generation == 0L || externalSwitches?.invoke()?.none { it.first == keyCode } != false) return false
            if (down) {
                if (activePresses[keyCode] == downTimeMs) return true
                activePresses[keyCode] = downTimeMs
            } else {
                if (activePresses[keyCode] != downTimeMs) return true
                activePresses.remove(keyCode)
            }
            edgeSequence += 1
            Edge(generation, edgeSequence, keyCode, down, downTimeMs, eventTimeMs, cancelled)
        }
        broadcast { it.onSwitchEdge(event.generation, event.sequence, event.keyCode, event.down, event.downTimeMs, event.eventTimeMs, event.cancelled) }
        return true
    }

    fun snapshot(): Bundle = synchronized(lock) {
        Bundle().apply {
            putInt("version", VERSION)
            putBoolean("captureAvailable", externalSwitches != null)
            putParcelableArrayList("externalSwitches", ArrayList(externalSwitches?.invoke().orEmpty().map { event -> Bundle().apply { putInt("keyCode", event.first); putString("name", event.second) } }))
        }
    }

    fun clearActive() = synchronized(lock) { clearActiveLocked() }
    internal fun currentEdgeSequence() = synchronized(lock) { edgeSequence }
    private fun clearActiveLocked() { repeatGeneration = 0; forwardingGeneration = 0; edgeSequence = 0; activePresses.clear(); ServiceCore.getScanningManager()?.resumeScanning() }
    private fun clearIfUnbound() { if (callbackCount == 0) clearActive() }
    private fun publishSnapshot() { if (callbackCount == 0) return; val value = snapshot(); broadcast { it.onSnapshot(value) } }
    private inline fun broadcast(block: (ISwitchifyRemoteBridgeCallback) -> Unit) { if (callbackCount == 0) return; val count = callbacks.beginBroadcast(); try { for (index in 0 until count) runCatching { block(callbacks.getBroadcastItem(index)) } } finally { callbacks.finishBroadcast() } }
    private data class Edge(val generation: Long, val sequence: Long, val keyCode: Int, val down: Boolean, val downTimeMs: Long, val eventTimeMs: Long, val cancelled: Boolean)
}
