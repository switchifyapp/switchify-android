package com.enaboapps.switchify.pc.forwarding

import com.enaboapps.switchify.service.remotebridge.InAppSwitchForwardingListener
import com.enaboapps.switchify.service.remotebridge.SwitchForwardingOwner
import com.enaboapps.switchify.service.remotebridge.SwitchifyRemoteBridgeCoordinator
import java.util.concurrent.atomic.AtomicLong

data class PcExternalSwitch(val keyCode: Int, val name: String)

data class PcSwitchBridgeSnapshot(val captureAvailable: Boolean, val externalSwitches: List<PcExternalSwitch>) {
    companion object {
        val Unavailable = PcSwitchBridgeSnapshot(captureAvailable = false, externalSwitches = emptyList())
    }
}

sealed class PcSwitchBridgeEvent {
    data class Snapshot(val snapshot: PcSwitchBridgeSnapshot) : PcSwitchBridgeEvent()

    data class SwitchEdge(
        val generation: Long,
        val sequence: Long,
        val keyCode: Int,
        val down: Boolean,
        val downTimeMs: Long,
        val eventTimeMs: Long,
        val cancelled: Boolean
    ) : PcSwitchBridgeEvent()

    data class Revoked(val generation: Long) : PcSwitchBridgeEvent()
}

interface PcSwitchBridge {
    fun snapshot(): PcSwitchBridgeSnapshot

    fun subscribe(listener: (PcSwitchBridgeEvent) -> Unit): () -> Unit

    fun nextGeneration(): Long

    fun setForwardingActive(generation: Long, active: Boolean): Boolean

    fun forwardingOwnedBySwitchifyRemote(): Boolean
}

object InAppSwitchBridge : PcSwitchBridge {
    private val generation = AtomicLong(0)

    override fun snapshot(): PcSwitchBridgeSnapshot {
        val (captureAvailable, switches) = SwitchifyRemoteBridgeCoordinator.inAppSnapshot()
        return snapshotOf(captureAvailable, switches)
    }

    override fun subscribe(listener: (PcSwitchBridgeEvent) -> Unit): () -> Unit =
        SwitchifyRemoteBridgeCoordinator.addInAppListener(object : InAppSwitchForwardingListener {
            override fun onSnapshot(captureAvailable: Boolean, externalSwitches: List<Pair<Int, String>>) =
                listener(PcSwitchBridgeEvent.Snapshot(snapshotOf(captureAvailable, externalSwitches)))

            override fun onSwitchEdge(
                generation: Long,
                sequence: Long,
                keyCode: Int,
                down: Boolean,
                downTimeMs: Long,
                eventTimeMs: Long,
                cancelled: Boolean
            ) = listener(PcSwitchBridgeEvent.SwitchEdge(generation, sequence, keyCode, down, downTimeMs, eventTimeMs, cancelled))

            override fun onForwardingRevoked(generation: Long) = listener(PcSwitchBridgeEvent.Revoked(generation))
        })

    override fun nextGeneration(): Long = generation.incrementAndGet()

    override fun setForwardingActive(generation: Long, active: Boolean): Boolean =
        SwitchifyRemoteBridgeCoordinator.setInAppForwardingActive(generation, active)

    override fun forwardingOwnedBySwitchifyRemote(): Boolean =
        SwitchifyRemoteBridgeCoordinator.activeForwardingOwner() == SwitchForwardingOwner.SwitchifyRemote

    internal fun resetForTests() = generation.set(0)

    private fun snapshotOf(captureAvailable: Boolean, switches: List<Pair<Int, String>>) =
        if (captureAvailable) {
            PcSwitchBridgeSnapshot(true, switches.map { (keyCode, name) -> PcExternalSwitch(keyCode, name) })
        } else {
            PcSwitchBridgeSnapshot.Unavailable
        }
}
