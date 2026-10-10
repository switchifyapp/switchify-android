package com.enaboapps.switchify.screens.pc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.connection.PcConnectionManager
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcList
import com.enaboapps.switchify.pc.connection.PcListItem
import com.enaboapps.switchify.pc.connection.PcPermissionRequester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val pcs: StateFlow<List<PcListItem>> = manager.state
        .map { current ->
            val discovered = (current as? PcConnectionState.Scanning)?.discovered.orEmpty()
            PcList.merge(current.savedPcs.orEmpty(), discovered)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _defaultDesktopId = MutableStateFlow<String?>(null)
    val defaultDesktopId: StateFlow<String?> = _defaultDesktopId.asStateFlow()

    init {
        permissions.attachHost()
        viewModelScope.launch {
            if (manager.state.value is PcConnectionState.Idle) manager.load()
        }
        viewModelScope.launch {
            manager.state.map { it.savedPcs }.distinctUntilChanged().collect { refreshDefault() }
        }
    }

    fun scan() {
        viewModelScope.launch { manager.scan() }
    }

    fun connect(item: PcListItem) {
        viewModelScope.launch {
            val saved = item.savedForConnection()
            if (saved != null) manager.connectSaved(saved) else manager.connect(item.desktop)
        }
    }

    fun disconnect() {
        viewModelScope.launch { manager.disconnect() }
    }

    fun unpair(desktopId: String) {
        viewModelScope.launch { manager.unpair(desktopId) }
    }

    fun setDefault(desktopId: String?) {
        viewModelScope.launch {
            manager.setDefaultDesktopId(desktopId)
            refreshDefault()
        }
    }

    fun onPermissionResult(granted: Boolean) = permissions.complete(granted)

    private suspend fun refreshDefault() {
        _defaultDesktopId.value = try {
            manager.defaultDesktopId()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        permissions.detachHost()
        manager.stopScan()
    }
}
