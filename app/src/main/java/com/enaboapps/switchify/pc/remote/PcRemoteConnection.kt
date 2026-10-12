package com.enaboapps.switchify.pc.remote

import com.enaboapps.switchify.pc.connection.PcConnectionManager
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcSendOutcome
import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.pc.transport.PcUnsubscribe
import kotlinx.coroutines.flow.StateFlow

interface PcRemoteConnection {
    val state: StateFlow<PcConnectionState>

    suspend fun send(command: PcCommand, responseMode: PcResponseMode): Boolean

    suspend fun sendWithOutcome(command: PcCommand, responseMode: PcResponseMode): PcSendOutcome =
        if (send(command, responseMode)) PcSendOutcome.Accepted else PcSendOutcome.Rejected

    fun registerCleanup(cleanup: suspend () -> Unit): PcUnsubscribe

    suspend fun connectPreferred()

    suspend fun listSaved(): List<PcSavedPc>

    suspend fun switchSaved(pc: PcSavedPc)
}

fun PcConnectionManager.asRemoteConnection(): PcRemoteConnection {
    val manager = this
    return object : PcRemoteConnection {
        override val state: StateFlow<PcConnectionState> = manager.state

        override suspend fun send(command: PcCommand, responseMode: PcResponseMode) = manager.send(command, responseMode)

        override suspend fun sendWithOutcome(command: PcCommand, responseMode: PcResponseMode) =
            manager.sendWithOutcome(command, responseMode)

        override fun registerCleanup(cleanup: suspend () -> Unit) = manager.registerCleanup(cleanup)

        override suspend fun connectPreferred() = manager.connectPreferred()

        override suspend fun listSaved() = manager.listSaved()

        override suspend fun switchSaved(pc: PcSavedPc) = manager.switchSaved(pc)
    }
}
