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

    private val autoConnect = PcAutoConnectPolicy()

    init {
        permissions.attachHost()
        if (setup.isComplete) resolveOpeningTab()
    }

    fun selectTab(tab: PcControlTab) {
        _tab.value = tab
    }

    fun onStart() {
        if (autoConnect.onStart(setup.isComplete)) launchSafely { connection.connectPreferred() }
    }

    fun onStop(changingConfigurations: Boolean) = autoConnect.onStop(changingConfigurations)

    fun onResume() {
        if (permissions.requested.value) permissions.complete(permissions.isGranted())
    }

    fun onPermissionResult(granted: Boolean) = permissions.complete(granted)

    fun continueSetup() = setup.next()

    fun backInSetup(): Boolean = setup.back()

    fun chooseOpeningSurface(surface: PcRemoteSurface) = preferences.setSurface(surface)

    fun finishSetup(searchForPcs: Boolean) {
        if (setup.isComplete) return
        setup.complete()
        _tab.value = PcControlOpening.tabAfterSetup()
        if (searchForPcs) launchSafely { connection.scan() }
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
            if (_tab.value == null) _tab.value = PcControlOpening.initialTab(saved)
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
                connection.cancelPreferredConnection()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }
}
