package com.enaboapps.switchify.service.remotebridge

import android.os.Handler
import android.os.Looper
import com.enaboapps.switchify.pc.remote.PcSwitchRepeatStop
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import java.util.concurrent.CopyOnWriteArraySet

object SwitchifyRemoteBridgeCoordinator {
    private val callbackDispatcher = SerializedCallbackDispatcher()
    private val lock = Any()
    private var externalSwitches: (() -> List<Pair<Int, String>>)? = null
    private var cameraSwitchesConfigured: () -> Boolean = { false }
    private var configuredSwitchFingerprint = emptyList<Pair<Int, String>>()
    private var forwardingGeneration = 0L
    private var forwardingGenerationHighWater = 0L
    private var forwardingActivation = 0L
    private var edgeSequence = 0L
    private val activePresses = mutableMapOf<Int, Long>()
    private val inAppListeners = CopyOnWriteArraySet<InAppSwitchForwardingListener>()
    private val scanningHolds = ScanningHolds()
    private var scanningHoldEpoch = 0L
    internal var setScanningPaused: (Boolean) -> Unit = ::postScanningPaused
    internal var postToMain: (() -> Unit) -> Unit = ::postToMainLooper
    internal var scanningHoldTarget: () -> ScanningHoldTarget? = ::serviceScanningHoldTarget
    @Volatile internal var pcRepeatActive: () -> Boolean = { false }

    fun attach(provider: SwitchEventProvider) = attach(provider::hasCameraSwitches) { provider.externalSwitches().mapNotNull { event -> event.code.toIntOrNull()?.let { it to event.name } } }
    internal fun attach(cameraSwitches: () -> Boolean = { false }, provider: () -> List<Pair<Int, String>>) = callbackDispatcher.dispatch {
        synchronized(lock) {
            externalSwitches = provider
            cameraSwitchesConfigured = cameraSwitches
            configuredSwitchFingerprint = configuredSwitchFingerprintLocked()
        }
        publishSnapshot()
        if (pcRepeatActive()) setScanningHeld(ScanningHold.PcRepeat) { pcRepeatActive() }
    }
    fun detach() = callbackDispatcher.dispatch {
        synchronized(lock) {
            externalSwitches = null
            cameraSwitchesConfigured = { false }
            configuredSwitchFingerprint = emptyList()
            clearForwardingLocked()
            scanningHolds.reset()
            scanningHoldEpoch += 1
        }
        publishSnapshot()
    }

    fun setForwardingActive(generation: Long, active: Boolean): Boolean = synchronized(lock) {
        if (externalSwitches == null || generation <= 0) return false
        if (active) {
            if (forwardingGeneration != 0L) return false
            if (generation <= forwardingGenerationHighWater) return false
            forwardingGenerationHighWater = generation
            forwardingGeneration = generation
            forwardingActivation += 1
            edgeSequence = 0
            activePresses.clear()
            setScanningPaused(true)
        } else {
            if (generation != forwardingGeneration) return false
            forwardingGeneration = 0
            activePresses.clear()
            setScanningPaused(false)
        }
        true
    }

    fun isForwardingActive(): Boolean = synchronized(lock) { forwardingGeneration != 0L }

    fun addInAppListener(listener: InAppSwitchForwardingListener): () -> Unit {
        inAppListeners += listener
        return { inAppListeners -= listener }
    }

    fun inAppSnapshot(): Pair<Boolean, List<Pair<Int, String>>> = synchronized(lock) {
        (externalSwitches != null) to externalSwitches?.invoke().orEmpty()
    }

    fun stopPcRepeatForExternalSwitch(keyCode: Int): Boolean {
        if (!isConfiguredExternalSwitch(keyCode)) return false
        return stopPcRepeatForSwitch()
    }

    fun stopPcRepeatForSwitch(): Boolean = PcSwitchRepeatStop.requestStop()

    fun configuredSwitchesChanged() = callbackDispatcher.dispatch {
        synchronized(lock) {
            val nextFingerprint = configuredSwitchFingerprintLocked()
            val changed = nextFingerprint != configuredSwitchFingerprint
            configuredSwitchFingerprint = nextFingerprint
            if (changed) clearForwardingLocked()
        }
        publishSnapshot()
    }

    fun hasConfiguredSwitches(): Boolean = synchronized(lock) {
        externalSwitches?.invoke()?.isNotEmpty() == true || cameraSwitchesConfigured()
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
        inAppListeners.forEach { listener ->
            runCatching {
                listener.onSwitchEdge(event.generation, event.sequence, event.keyCode, event.down, event.downTimeMs, event.eventTimeMs, event.cancelled)
            }
        }
        true
    }

    fun clearActive() = synchronized(lock) { clearForwardingLocked() }
    internal fun activeForwardingGeneration() = synchronized(lock) { if (forwardingGeneration == 0L) 0L else forwardingActivation }
    internal fun currentEdgeSequence() = synchronized(lock) { edgeSequence }
    internal fun resetForTests() = synchronized(lock) {
        clearForwardingLocked()
        forwardingGenerationHighWater = 0
        inAppListeners.clear()
        scanningHolds.reset()
        scanningHoldEpoch += 1
        setScanningPaused = ::postScanningPaused
        postToMain = ::postToMainLooper
        scanningHoldTarget = ::serviceScanningHoldTarget
        pcRepeatActive = { false }
    }
    private fun isConfiguredExternalSwitch(keyCode: Int) = synchronized(lock) {
        externalSwitches?.invoke()?.any { it.first == keyCode } == true
    }
    private fun configuredSwitchFingerprintLocked() = externalSwitches?.invoke().orEmpty()
        .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second })
        .take(8)
    private fun clearForwardingLocked() {
        val generation = forwardingGeneration
        forwardingGeneration = 0; edgeSequence = 0; activePresses.clear()
        if (generation == 0L) return
        setScanningPaused(false)
        inAppListeners.forEach { listener -> runCatching { listener.onForwardingRevoked(generation) } }
    }
    private fun postScanningPaused(paused: Boolean) = setScanningHeld(ScanningHold.Forwarding, paused)

    fun setScanningHeld(hold: ScanningHold, held: Boolean) = setScanningHeld(hold) { held }
    private fun setScanningHeld(hold: ScanningHold, held: () -> Boolean) {
        val epoch = synchronized(lock) { scanningHoldEpoch }
        postToMain {
            val target = scanningHoldTarget()
            synchronized(lock) {
                if (epoch == scanningHoldEpoch) scanningHolds.apply(hold, held(), target)
            }
        }
    }
    private fun postToMainLooper(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }
    private fun serviceScanningHoldTarget(): ScanningHoldTarget? {
        val scanningManager = ServiceCore.getScanningManager() ?: return null
        return object : ScanningHoldTarget {
            override fun pauseScanning() = scanningManager.pauseScanning()
            override fun resumeScanning() = scanningManager.resumeScanning()
            override fun isPausedByUser() = ServiceCore.getPauseManager().isPaused
        }
    }
    private fun publishSnapshot() {
        val (captureAvailable, switches) = inAppSnapshot()
        inAppListeners.forEach { listener -> runCatching { listener.onSnapshot(captureAvailable, switches) } }
    }
    private data class Edge(val generation: Long, val sequence: Long, val keyCode: Int, val down: Boolean, val downTimeMs: Long, val eventTimeMs: Long, val cancelled: Boolean)
}

internal class SerializedCallbackDispatcher {
    private val lock = Any()

    fun <T> dispatch(block: () -> T): T = synchronized(lock) { block() }
}
