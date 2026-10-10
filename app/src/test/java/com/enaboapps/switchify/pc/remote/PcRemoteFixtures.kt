package com.enaboapps.switchify.pc.remote

import com.enaboapps.switchify.pc.protocol.PcBounds
import com.enaboapps.switchify.pc.protocol.PcCapabilities
import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcDisplayNavigationCapability
import com.enaboapps.switchify.pc.protocol.PcJson
import com.enaboapps.switchify.pc.protocol.PcKeyRepeatCapability
import com.enaboapps.switchify.pc.protocol.PcMouseRepeatCapability
import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.protocol.PcPointerSpeedCapability
import com.enaboapps.switchify.pc.protocol.PcRecommendedDeltas
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import kotlinx.coroutines.CompletableDeferred

val allRemoteCommands = listOf(
    "mouse.move", "mouse.click", "mouse.doubleClick", "mouse.rightClick", "mouse.scroll",
    "mouse.dragStart", "mouse.dragEnd", "mouse.repeat.start", "mouse.repeat.stop",
    "pointer.speed.set", "pointer.display.move", "keyboard.key", "keyboard.modifierDown",
    "keyboard.modifierUp", "keyboard.shortcut", "keyboard.typeText", "keyboard.textStream.open",
    "keyboard.textStream.chunk", "keyboard.textStream.key", "keyboard.textStream.close", "window.control"
)

val repeatableFixtureKeys = listOf(
    "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Tab", "Backspace", "Delete", "PageUp", "PageDown"
)

fun remoteProfile(
    supportedCommands: List<String> = allRemoteCommands,
    mouseRepeat: Boolean = true,
    keyRepeat: Boolean = true,
    keyRepeatEnabled: Boolean = keyRepeat,
    repeatableKeys: List<String> = if (keyRepeat) repeatableFixtureKeys else emptyList(),
    noAckCommands: List<String> = emptyList(),
    scalePercent: Double = 100.0,
    displayCount: Double = 2.0
) = PcPointerProfile(
    displayId = "display",
    scaleFactor = 1.0,
    bounds = PcBounds(0.0, 0.0, 100.0, 100.0),
    maxDelta = 128.0,
    recommendedDeltas = PcRecommendedDeltas(32.0, 64.0, 128.0),
    capabilities = PcCapabilities(
        noAckMouseMove = false,
        switchScanning = false,
        noAckCommands = noAckCommands,
        supportedCommands = supportedCommands,
        mouseRepeat = PcMouseRepeatCapability(mouseRepeat, mouseRepeat, 250.0, 100.0, 2000.0),
        keyRepeat = PcKeyRepeatCapability(keyRepeat, keyRepeatEnabled, 250.0, 500.0, 100.0, 1000.0, repeatableKeys),
        pointerSpeed = PcPointerSpeedCapability(true, true, scalePercent, 5.0, 225.0, 5.0, 64.0, 64.0),
        displayNavigation = PcDisplayNavigationCapability(true, displayCount)
    )
)

data class SentCommand(val type: String, val payload: String, val mode: PcResponseMode)

class FakeRemoteSender : PcRemoteSender {
    val calls = mutableListOf<SentCommand>()
    val gates = mutableMapOf<String, CompletableDeferred<Boolean>>()
    var result: (PcCommand) -> Boolean = { true }

    val types: List<String> get() = calls.map { it.type }

    override suspend fun send(command: PcCommand, responseMode: PcResponseMode): Boolean {
        calls += SentCommand(command.type, PcJson.stringify(command.payload), responseMode)
        gates.remove(command.type)?.let { return it.await() }
        return result(command)
    }

    fun gate(type: String): CompletableDeferred<Boolean> = CompletableDeferred<Boolean>().also { gates[type] = it }
}

class FakeSwitchStop(var available: Boolean = true) : PcRepeatSwitchStop {
    var armed: (() -> Unit)? = null
    var armCount = 0
    var releaseCount = 0

    override fun arm(onStop: () -> Unit): PcSwitchStopHandle {
        armCount += 1
        armed = onStop
        return PcSwitchStopHandle {
            releaseCount += 1
            if (armed === onStop) armed = null
        }
    }

    override fun isAvailable() = available

    fun press(): Boolean {
        val stop = armed ?: return false
        armed = null
        stop()
        return true
    }
}
