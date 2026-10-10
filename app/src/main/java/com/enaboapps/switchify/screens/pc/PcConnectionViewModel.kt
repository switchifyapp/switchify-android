package com.enaboapps.switchify.screens.pc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.connection.PcConnectionManager
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcList
import com.enaboapps.switchify.pc.connection.PcListItem
import com.enaboapps.switchify.pc.connection.PcPermissionRequester
import com.enaboapps.switchify.pc.storage.PcSavedPc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PcConnectionViewModel(
    private val manager: PcConnectionManager,
    private val permissions: PcPermissionRequester
) : ViewModel() {
    val state: StateFlow<PcConnectionState> = manager.state
    val permissionRequested: StateFlow<Boolean> = permissions.requested

    private val connectedSaved = MutableStateFlow<List<PcSavedPc>>(emptyList())

    val pcs: StateFlow<List<PcListItem>> = combine(manager.state, connectedSaved) { current, savedWhileConnected ->
        val saved = current.savedPcs ?: if (current is PcConnectionState.Connected) savedWhileConnected else emptyList()
        PcList.merge(saved, (current as? PcConnectionState.Scanning)?.discovered.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _defaultDesktopId = MutableStateFlow<String?>(null)
    val defaultDesktopId: StateFlow<String?> = _defaultDesktopId.asStateFlow()

    private val _operationFailed = MutableStateFlow(false)
    val operationFailed: StateFlow<Boolean> = _operationFailed.asStateFlow()

    init {
        permissions.attachHost()
        launchSafely {
            if (manager.state.value is PcConnectionState.Idle) manager.load()
        }
        viewModelScope.launch {
            manager.state
                .map { it.javaClass to it.savedPcs }
                .distinctUntilChanged()
                .collect { refreshQuietly() }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshQuietly() }
    }

    private suspend fun refreshQuietly() {
        try {
            refreshSaved()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }

    fun scan() = launchSafely { manager.scan() }

    fun connect(item: PcListItem) = launchSafely {
        val saved = item.savedForConnection()
        when {
            saved == null -> manager.connect(item.desktop)
            manager.state.value is PcConnectionState.Connected -> manager.switchSaved(saved)
            else -> manager.connectSaved(saved)
        }
    }

    fun disconnect() = launchSafely { manager.disconnect() }

    fun unpair(desktopId: String) = launchSafely {
        manager.unpair(desktopId)
        refreshSaved()
    }

    fun setDefault(desktopId: String?) = launchSafely {
        manager.setDefaultDesktopId(desktopId)
        refreshSaved()
    }

    fun stopScan() {
        manager.stopScan()
    }

    fun onResume() {
        if (permissions.requested.value) permissions.complete(permissions.isGranted())
    }

    fun onPermissionResult(granted: Boolean) = permissions.complete(granted)

    fun dismissFailure() {
        _operationFailed.value = false
    }

    private suspend fun refreshSaved() {
        _defaultDesktopId.value = manager.defaultDesktopId()
        if (manager.state.value is PcConnectionState.Connected) connectedSaved.value = manager.listSaved()
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                _operationFailed.value = false
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _operationFailed.value = true
            }
        }
    }

    override fun onCleared() {
        permissions.detachHost()
        manager.stopScan()
    }
}
