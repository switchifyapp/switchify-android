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

    suspend fun connectSaved(pc: PcSavedPc)

    fun isLocationOff(): Boolean

    suspend fun disconnect()

    suspend fun scan()

    fun stopScan()

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

        override suspend fun connectSaved(pc: PcSavedPc) = manager.connectSaved(pc)

        override fun isLocationOff() = manager.isLocationOff()

        override suspend fun disconnect() = manager.disconnect()

        override suspend fun scan() = manager.scan()

        override fun stopScan() {
            manager.stopScan()
        }

        override suspend fun defaultDesktopId() = manager.defaultDesktopId()

        override suspend fun setDefaultDesktopId(desktopId: String?) = manager.setDefaultDesktopId(desktopId)

        override suspend fun syncRemoteName() = manager.syncRemoteName()
    }
}
