package com.enaboapps.switchify.screens.pc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.connection.PcPermissionRequester
import com.enaboapps.switchify.pc.control.PcAutoConnectPolicy
import com.enaboapps.switchify.pc.control.PcControlConnection
import com.enaboapps.switchify.pc.control.PcControlOpening
import com.enaboapps.switchify.pc.control.PcControlSetup
import com.enaboapps.switchify.pc.control.PcControlSetupPhase
import com.enaboapps.switchify.pc.control.PcControlTab
import com.enaboapps.switchify.pc.control.PcResumeRecovery
import com.enaboapps.switchify.pc.remote.PcRemotePreferences
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PcControlViewModel(
    private val connection: PcControlConnection,
    private val setup: PcControlSetup,
    private val permissions: PcPermissionRequester,
    private val preferences: PcRemotePreferences,
    private val cleanupScope: CoroutineScope
) : ViewModel() {
    val setupPhase: StateFlow<PcControlSetupPhase> = setup.phase
    val surface: StateFlow<PcRemoteSurface> = preferences.surface
    val permissionRequested: StateFlow<Boolean> = permissions.requested

    private val _tab = MutableStateFlow<PcControlTab?>(null)
    val tab: StateFlow<PcControlTab?> = _tab.asStateFlow()

    private val _startTab = MutableStateFlow<PcControlTab?>(null)
    val startTab: StateFlow<PcControlTab?> = _startTab.asStateFlow()

    private val autoConnect = PcAutoConnectPolicy()

    init {
        permissions.attachHost()
        if (setup.isComplete) resolveOpeningTab()
    }

    fun selectTab(tab: PcControlTab) {
        _tab.value = tab
    }

    fun onBack(): Boolean {
        val start = _startTab.value ?: return false
        if (_tab.value == start) return false
        _tab.value = start
        return true
    }

    fun onStart() {
        val shouldConnect = autoConnect.onStart(setup.isComplete)
        if (shouldConnect && PcResumeRecovery.pendingRetry(connection.state.value) == null) {
            launchSafely { connection.connectPreferred() }
        }
    }

    fun onStop(changingConfigurations: Boolean) = autoConnect.onStop(changingConfigurations)

    fun onResume() {
        if (permissions.requested.value) permissions.complete(permissions.isGranted())
        val recovery = PcResumeRecovery.after(connection.state.value, permissions.isGranted(), connection.isLocationOff())
        if (recovery is PcResumeRecovery.RetrySaved) launchSafely { connection.connectSaved(recovery.pc) }
    }

    fun onPermissionResult(granted: Boolean) = permissions.complete(granted)

    fun continueSetup() = setup.next()

    fun backInSetup(): Boolean = setup.back()

    fun chooseOpeningSurface(surface: PcRemoteSurface) = preferences.setSurface(surface)

    fun finishSetup(searchForPcs: Boolean) {
        if (setup.isComplete) return
        setup.complete()
        open(PcControlOpening.tabAfterSetup())
        if (searchForPcs) launchSafely { connection.scan() }
    }

    private fun open(tab: PcControlTab) {
        _startTab.value = tab
        _tab.value = tab
    }

    private fun resolveOpeningTab() {
        viewModelScope.launch {
            val saved = try {
                connection.listSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (_tab.value == null) open(PcControlOpening.initialTab(saved))
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        permissions.detachHost()
        cleanupScope.launch {
            try {
                connection.disconnect()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }
}
