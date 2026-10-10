package com.enaboapps.switchify.pc.forwarding

import com.enaboapps.switchify.pc.protocol.PcBounds
import com.enaboapps.switchify.pc.protocol.PcCapabilities
import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcDisplayNavigationCapability
import com.enaboapps.switchify.pc.protocol.PcKeyRepeatCapability
import com.enaboapps.switchify.pc.protocol.PcMouseRepeatCapability
import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.protocol.PcPointerSpeedCapability
import com.enaboapps.switchify.pc.protocol.PcRecommendedDeltas
import com.enaboapps.switchify.pc.protocol.PcResponse
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import com.enaboapps.switchify.pc.protocol.PcSwitchBinding
import com.enaboapps.switchify.pc.protocol.PcSwitchBindingBehavior
import com.enaboapps.switchify.pc.protocol.PcSwitchProfile
import com.enaboapps.switchify.pc.protocol.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.protocol.PcSwitchProfileKind

internal val GENERIC_COMMANDS = PcForwardingController.GENERIC_COMMANDS

internal fun pointerProfile(
    commands: List<String>,
    noAckCommands: List<String> = emptyList(),
    switchScanning: Boolean = false
) = PcPointerProfile(
    displayId = "d",
    scaleFactor = 1.0,
    bounds = PcBounds(0.0, 0.0, 1.0, 1.0),
    maxDelta = 1.0,
    recommendedDeltas = PcRecommendedDeltas(1.0, 1.0, 1.0),
    capabilities = PcCapabilities(
        noAckMouseMove = false,
        switchScanning = switchScanning,
        noAckCommands = noAckCommands,
        supportedCommands = commands,
        mouseRepeat = PcMouseRepeatCapability(false, false, 250.0, 100.0, 2_000.0),
        keyRepeat = PcKeyRepeatCapability(false, false, 250.0, 500.0, 100.0, 1_000.0, emptyList()),
        pointerSpeed = PcPointerSpeedCapability(false, false, 100.0, 5.0, 225.0, 5.0, 1.0, 1.0),
        displayNavigation = PcDisplayNavigationCapability(false, 1.0)
    )
)

internal val keyboardProfile = PcSwitchProfile(
    id = "keyboard",
    version = 2,
    name = "Keyboard",
    kind = PcSwitchProfileKind.Mapped,
    bindings = listOf(PcSwitchBinding(1, "Space", PcSwitchBindingBehavior.Stateful))
)

internal val scanningProfile = PcSwitchProfile(
    id = "builtin.switchify-scanning",
    version = 1,
    name = "Switchify scanning",
    kind = PcSwitchProfileKind.Scanning,
    bindings = listOf(PcSwitchBinding(1, "Select", PcSwitchBindingBehavior.Stateful))
)

internal fun catalog(vararg profiles: PcSwitchProfile): PcResponse =
    PcResponse.SwitchProfileCatalog("catalog", PcSwitchProfileCatalog(1, profiles.toList()))

internal class FakeBridge : PcSwitchBridge {
    var value = PcSwitchBridgeSnapshot(true, (0 until 10).map { PcExternalSwitch(20 + it, "Switch ${it + 1}") })
    val listeners = mutableListOf<(PcSwitchBridgeEvent) -> Unit>()
    val active = mutableListOf<Pair<Long, Boolean>>()
    var generation = 40L
    var acceptActivation = true
    var remoteOwnsSwitches = false

    override fun snapshot() = value

    override fun subscribe(listener: (PcSwitchBridgeEvent) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    override fun nextGeneration() = ++generation

    override fun setForwardingActive(generation: Long, active: Boolean): Boolean {
        this.active += generation to active
        return !active || acceptActivation
    }

    override fun forwardingOwnedBySwitchifyRemote() = remoteOwnsSwitches

    fun emit(event: PcSwitchBridgeEvent) = listeners.toList().forEach { it(event) }

    fun edge(
        sequence: Long,
        keyCode: Int,
        down: Boolean,
        downTimeMs: Long,
        eventTimeMs: Long,
        cancelled: Boolean = false,
        generation: Long = 41
    ) = emit(PcSwitchBridgeEvent.SwitchEdge(generation, sequence, keyCode, down, downTimeMs, eventTimeMs, cancelled))
}

internal data class SentCommand(val type: String, val payload: Map<String, Any?>, val responseMode: PcResponseMode)

internal class FakeConnection(var response: PcResponse? = catalog(keyboardProfile)) : PcForwardingConnection {
    val requests = mutableListOf<PcCommand>()
    val sent = mutableListOf<SentCommand>()
    var onSend: suspend (PcCommand, PcResponseMode) -> Boolean = { _, _ -> true }

    override suspend fun request(command: PcCommand, responseMode: PcResponseMode): PcResponse? {
        requests += command
        return response
    }

    override suspend fun send(command: PcCommand, responseMode: PcResponseMode): Boolean {
        sent += SentCommand(command.type, command.payload, responseMode)
        return onSend(command, responseMode)
    }

    fun of(type: String) = sent.filter { it.type == type }

    fun edgeStates(type: String = "switch.edge") = of(type).map { it.payload["state"] }
}

internal class FakePreferences : PcForwardingPreferenceStore {
    var holdToStop = PcForwardingController.DEFAULT_HOLD_TO_STOP_MS
    val profiles = mutableMapOf<String, String>()
    var failSaves = false

    override fun holdToStopMs() = holdToStop

    override fun setHoldToStopMs(value: Long) {
        holdToStop = PcForwardingPreferences.normalizeHoldToStop(value)
    }

    override fun rememberedProfileId(desktopId: String) = profiles[desktopId]

    override fun rememberProfileId(desktopId: String, profileId: String) {
        check(!failSaves)
        profiles[desktopId] = profileId
    }
}
