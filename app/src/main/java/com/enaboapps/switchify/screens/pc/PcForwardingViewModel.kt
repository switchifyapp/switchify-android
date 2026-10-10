package com.enaboapps.switchify.screens.pc

import androidx.lifecycle.ViewModel
import com.enaboapps.switchify.pc.connection.PcConnectionManager
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.forwarding.PcForwardingConnection
import com.enaboapps.switchify.pc.forwarding.PcForwardingPreferenceStore
import com.enaboapps.switchify.pc.forwarding.PcForwardingSession
import com.enaboapps.switchify.pc.forwarding.PcForwardingState
import com.enaboapps.switchify.pc.forwarding.PcSwitchBridge
import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcResponse
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PcForwardingViewModel(
    private val manager: PcConnectionManager,
    bridge: PcSwitchBridge,
    preferences: PcForwardingPreferenceStore
) : ViewModel() {
    private val forwardingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })

    private val session = PcForwardingSession(
        connectionState = manager.state,
        connection = object : PcForwardingConnection {
            override suspend fun request(command: PcCommand, responseMode: PcResponseMode): PcResponse? =
                manager.request(command, responseMode)

            override suspend fun send(command: PcCommand, responseMode: PcResponseMode): Boolean =
                manager.send(command, responseMode)
        },
        registerCleanup = manager::registerCleanup,
        bridge = bridge,
        preferences = preferences,
        scope = forwardingScope
    )

    val connection: StateFlow<PcConnectionState> = manager.state
    val forwarding: StateFlow<PcForwardingState> = session.state
    val holdToStopMs: StateFlow<Long> = session.holdToStopMs

    fun toggle() = session.toggle()

    fun selectProfile(profileId: String) = session.selectProfile(profileId)

    fun setHoldToStopMs(value: Long) = session.setHoldToStopMs(value)

    fun refreshHoldToStop() = session.refreshHoldToStop()

    fun attach() = session.attach()

    fun detach(changingConfigurations: Boolean) = session.detach(changingConfigurations)

    override fun onCleared() {
        val closing = session.close()
        forwardingScope.launch {
            withTimeoutOrNull(CLEANUP_TIMEOUT_MS) { closing.join() }
            forwardingScope.cancel()
        }
    }

    private companion object {
        const val CLEANUP_TIMEOUT_MS = 10_000L
    }
}
