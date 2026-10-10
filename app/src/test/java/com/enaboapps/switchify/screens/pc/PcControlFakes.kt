package com.enaboapps.switchify.screens.pc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcRemoteNameSync
import com.enaboapps.switchify.pc.control.PcControlConnection
import com.enaboapps.switchify.pc.storage.PcSavedPc
import kotlinx.coroutines.flow.MutableStateFlow

class FakePcControlConnection(var saved: List<PcSavedPc> = emptyList()) : PcControlConnection {
    override val state = MutableStateFlow<PcConnectionState>(PcConnectionState.Idle(saved))
    var connectCalls = 0
    var disconnectCalls = 0
    val connectedSaved = mutableListOf<PcSavedPc>()
    var locationOff = false
    var scanCalls = 0
    var syncCalls = 0
    var defaultId: String? = null
    var sync = PcRemoteNameSync.Deferred

    override suspend fun listSaved(): List<PcSavedPc> = saved

    override suspend fun connectPreferred() {
        connectCalls += 1
    }

    override suspend fun disconnect() {
        disconnectCalls += 1
    }

    override suspend fun connectSaved(pc: PcSavedPc) {
        connectedSaved += pc
    }

    override fun isLocationOff() = locationOff

    override suspend fun scan() {
        scanCalls += 1
    }

    override suspend fun defaultDesktopId(): String? = defaultId

    override suspend fun setDefaultDesktopId(desktopId: String?) {
        defaultId = desktopId
    }

    override suspend fun syncRemoteName(): PcRemoteNameSync {
        syncCalls += 1
        return sync
    }
}

fun ViewModel.clearForTest() {
    val store = ViewModelStore()
    val viewModel = this
    ViewModelProvider(store, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
    })[javaClass]
    store.clear()
}
