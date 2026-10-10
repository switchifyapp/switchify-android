package com.enaboapps.switchify.pc.forwarding

import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.protocol.PcSwitchProfileKind
import com.enaboapps.switchify.pc.transport.PcUnsubscribe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class PcForwardingSession(
    private val connectionState: StateFlow<PcConnectionState>,
    private val connection: PcForwardingConnection,
    private val registerCleanup: (suspend () -> Unit) -> PcUnsubscribe,
    private val bridge: PcSwitchBridge,
    private val preferences: PcForwardingPreferenceStore,
    private val scope: CoroutineScope,
    private val sessionIds: () -> String = { UUID.randomUUID().toString() }
) {
    private enum class Visibility { Visible, Rotating, Hidden }

    private class Active(
        val desktopId: String,
        val profile: PcPointerProfile,
        val controller: PcForwardingController,
        val stateJob: Job,
        val unregisterCleanup: PcUnsubscribe
    ) {
        var loaded = false
    }

    private val _state = MutableStateFlow(PcForwardingState())
    val state: StateFlow<PcForwardingState> = _state.asStateFlow()

    private val _holdToStopMs = MutableStateFlow(preferences.holdToStopMs())
    val holdToStopMs: StateFlow<Long> = _holdToStopMs.asStateFlow()

    private val restore = PcForwardingRestoreState()
    private var active: Active? = null
    private var visibility = Visibility.Hidden
    private var leaves = 0
    private val connectionJob: Job = scope.launch { connectionState.collect(::onConnection) }

    fun toggle() {
        val current = active ?: return
        scope.launch {
            val controller = current.controller
            if (controller.state.value.phase == PcForwardingPhase.Active) {
                restore.clear()
                controller.stop()
            } else {
                val leavesBefore = leaves
                if (!controller.start()) return@launch
                if (leftSince(leavesBefore, current)) {
                    controller.stop(PcForwardingMessage.LeftScreen)
                    return@launch
                }
                val selected = controller.selectedProfile()
                if (selected != null && selected.kind != PcSwitchProfileKind.Scanning) {
                    restore.set(PcForwardingRestoreIntent(current.desktopId, selected.id, selected.version))
                }
            }
        }
    }

    fun selectProfile(profileId: String) {
        val current = active ?: return
        try {
            preferences.rememberProfileId(current.desktopId, profileId)
            current.controller.selectProfile(profileId)
        } catch (_: Exception) {
            current.controller.report(PcForwardingMessage.SelectionSaveFailed)
        }
    }

    fun setHoldToStopMs(value: Long) {
        preferences.setHoldToStopMs(value)
        _holdToStopMs.value = preferences.holdToStopMs()
    }

    fun refreshHoldToStop() {
        _holdToStopMs.value = preferences.holdToStopMs()
    }

    fun attach() {
        visibility = Visibility.Visible
        active?.let(::restoreIfVisible)
    }

    fun detach(changingConfigurations: Boolean) {
        if (changingConfigurations) {
            if (visibility == Visibility.Visible) visibility = Visibility.Rotating
            return
        }
        visibility = Visibility.Hidden
        leaves += 1
        restore.clear()
        val controller = active?.controller ?: return
        if (!controller.state.value.running) return
        scope.launch { controller.stop(PcForwardingMessage.LeftScreen) }
    }

    fun close(): Job {
        connectionJob.cancel()
        return dispose() ?: Job().apply { complete() }
    }

    private fun onConnection(state: PcConnectionState) {
        if (PcForwardingRestoreState.shouldClear(state)) restore.clear()
        val profile = (state as? PcConnectionState.Connected)?.profile
        if (state !is PcConnectionState.Connected || profile == null) {
            dispose()
            _state.value = PcForwardingState()
            return
        }
        val current = active
        if (current != null && current.desktopId == state.desktop.desktopId && current.profile == profile) return
        dispose()
        create(state.desktop.desktopId, profile)
    }

    private fun create(desktopId: String, profile: PcPointerProfile) {
        val controller = PcForwardingController(
            connection = connection,
            bridge = bridge,
            pointerProfile = profile,
            scope = scope,
            holdToStopMs = { _holdToStopMs.value },
            sessionIds = sessionIds,
            onSafetyStop = { restore.clear() }
        )
        val stateJob = scope.launch { controller.state.collect { _state.value = it } }
        val unregister = registerCleanup {
            scope.launch {
                restore.clear()
                controller.cleanup()
            }.join()
        }
        val next = Active(desktopId, profile, controller, stateJob, unregister)
        active = next
        scope.launch {
            controller.loadProfiles(preferences.rememberedProfileId(desktopId))
            if (active !== next) return@launch
            next.loaded = true
            restoreIfVisible(next)
        }
    }

    private fun restoreIfVisible(current: Active) {
        if (visibility != Visibility.Visible || !current.loaded) return
        val intent = restore.get() ?: return
        if (intent.desktopId != current.desktopId) return
        val controller = current.controller
        if (controller.state.value.running) return
        val selected = controller.selectedProfile()
        if (selected?.id != intent.profileId || selected.version != intent.profileVersion) {
            restore.clear()
            controller.report(PcForwardingMessage.ProfileChanged)
            return
        }
        if (selected.kind == PcSwitchProfileKind.Scanning) {
            restore.clear()
            return
        }
        scope.launch {
            if (visibility != Visibility.Visible || active !== current || restore.get() != intent) return@launch
            val leavesBefore = leaves
            if (controller.start() && leftSince(leavesBefore, current)) controller.stop(PcForwardingMessage.LeftScreen)
        }
    }

    private fun leftSince(leavesBefore: Int, current: Active): Boolean =
        leaves != leavesBefore || visibility == Visibility.Hidden || active !== current

    private fun dispose(): Job? {
        val current = active ?: return null
        active = null
        current.unregisterCleanup.unsubscribe()
        current.stateJob.cancel()
        return scope.launch { current.controller.cleanup() }
    }
}
