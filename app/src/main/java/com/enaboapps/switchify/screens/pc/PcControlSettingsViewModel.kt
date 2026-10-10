package com.enaboapps.switchify.screens.pc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcRemoteNameStore
import com.enaboapps.switchify.pc.connection.PcRemoteNameSync
import com.enaboapps.switchify.pc.control.PcControlConnection
import com.enaboapps.switchify.pc.forwarding.PcForwardingPreferenceStore
import com.enaboapps.switchify.pc.remote.PcRemotePreferences
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.pc.remote.PcTypingMode
import com.enaboapps.switchify.pc.storage.PcSavedPc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class PcRemoteNameStatus {
    Synced,
    Deferred,
    SyncFailed,
    SaveFailed
}

class PcControlSettingsViewModel(
    private val connection: PcControlConnection,
    val preferences: PcRemotePreferences,
    private val remoteNames: PcRemoteNameStore,
    private val forwarding: PcForwardingPreferenceStore
) : ViewModel() {
    val state: StateFlow<PcConnectionState> = connection.state

    private val _saved = MutableStateFlow<List<PcSavedPc>>(emptyList())
    val saved: StateFlow<List<PcSavedPc>> = _saved.asStateFlow()

    private val _defaultDesktopId = MutableStateFlow<String?>(null)
    val defaultDesktopId: StateFlow<String?> = _defaultDesktopId.asStateFlow()

    private val _holdToStopMs = MutableStateFlow(forwarding.holdToStopMs())
    val holdToStopMs: StateFlow<Long> = _holdToStopMs.asStateFlow()

    private val _remoteName = MutableStateFlow(remoteNames.savedName())
    val remoteName: StateFlow<String?> = _remoteName.asStateFlow()

    val automaticRemoteName: String get() = remoteNames.automaticName

    private val _remoteNameSaving = MutableStateFlow(false)
    val remoteNameSaving: StateFlow<Boolean> = _remoteNameSaving.asStateFlow()

    private val _remoteNameStatus = MutableStateFlow<PcRemoteNameStatus?>(null)
    val remoteNameStatus: StateFlow<PcRemoteNameStatus?> = _remoteNameStatus.asStateFlow()

    init {
        viewModelScope.launch {
            connection.state
                .map { it.javaClass }
                .distinctUntilChanged()
                .collect { refreshSaved() }
        }
    }

    fun setSurface(surface: PcRemoteSurface) = preferences.setSurface(surface)

    fun setTypingMode(mode: PcTypingMode) = preferences.setTypingMode(mode)

    fun setHoldToStopMs(value: Long) {
        forwarding.setHoldToStopMs(value)
        _holdToStopMs.value = forwarding.holdToStopMs()
    }

    fun setDefault(desktopId: String?) {
        viewModelScope.launch {
            try {
                connection.setDefaultDesktopId(desktopId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
            refreshSaved()
        }
    }

    fun clearRemoteNameStatus() {
        _remoteNameStatus.value = null
    }

    fun saveRemoteName(name: String?) {
        if (_remoteNameSaving.value) return
        _remoteNameSaving.value = true
        _remoteNameStatus.value = null
        val saved = try {
            remoteNames.save(name)
            _remoteName.value = remoteNames.savedName()
            true
        } catch (_: Exception) {
            false
        }
        if (!saved) {
            _remoteNameSaving.value = false
            _remoteNameStatus.value = PcRemoteNameStatus.SaveFailed
            return
        }
        viewModelScope.launch {
            _remoteNameStatus.value = try {
                when (connection.syncRemoteName()) {
                    PcRemoteNameSync.Synced -> PcRemoteNameStatus.Synced
                    PcRemoteNameSync.Deferred -> PcRemoteNameStatus.Deferred
                    PcRemoteNameSync.Failed -> PcRemoteNameStatus.SyncFailed
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                PcRemoteNameStatus.SyncFailed
            } finally {
                _remoteNameSaving.value = false
            }
        }
    }

    private suspend fun refreshSaved() {
        try {
            _saved.value = connection.listSaved()
            _defaultDesktopId.value = connection.defaultDesktopId()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }
}
