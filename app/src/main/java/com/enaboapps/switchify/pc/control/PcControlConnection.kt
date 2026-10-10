package com.enaboapps.switchify.pc.control

import com.enaboapps.switchify.pc.connection.PcConnectionManager
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcRemoteNameSync
import com.enaboapps.switchify.pc.storage.PcSavedPc
import kotlinx.coroutines.flow.StateFlow

interface PcControlConnection {
    val state: StateFlow<PcConnectionState>

    suspend fun listSaved(): List<PcSavedPc>

    suspend fun connectPreferred()

    suspend fun cancelPreferredConnection()

    suspend fun scan()

    suspend fun defaultDesktopId(): String?

    suspend fun setDefaultDesktopId(desktopId: String?)

    suspend fun syncRemoteName(): PcRemoteNameSync
}

fun PcConnectionManager.asControlConnection(): PcControlConnection {
    val manager = this
    return object : PcControlConnection {
        override val state: StateFlow<PcConnectionState> = manager.state

        override suspend fun listSaved() = manager.listSaved()

        override suspend fun connectPreferred() = manager.connectPreferred()

        override suspend fun cancelPreferredConnection() = manager.cancelPreferredConnection()

        override suspend fun scan() = manager.scan()

        override suspend fun defaultDesktopId() = manager.defaultDesktopId()

        override suspend fun setDefaultDesktopId(desktopId: String?) = manager.setDefaultDesktopId(desktopId)

        override suspend fun syncRemoteName() = manager.syncRemoteName()
    }
}
