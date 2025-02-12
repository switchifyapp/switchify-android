package com.enaboapps.switchify.screens.settings.switches.models

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.switches.SwitchEventStore.RemoteSwitchInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Switches screen, handling switch events and remote switch operations.
 */
class SwitchesScreenModel : ViewModel() {
    private val store = SwitchEventStore.getInstance()
    private val _uiState = MutableStateFlow(SwitchesUiState())
    val uiState: StateFlow<SwitchesUiState> = _uiState

    private val numberOfSwitchesLimit = 3

    fun setup(context: Context) {
        observeSwitches(context)
        checkProStatus()
    }

    private fun checkProStatus() {
        viewModelScope.launch {
            val isPro = IAPHandler.hasPurchasedPro()
            _uiState.value = _uiState.value.copy(
                shouldLimitSwitches = !isPro
            )
        }
    }

    fun isAnotherSwitchAllowed(): Boolean {
        if (!_uiState.value.shouldLimitSwitches) {
            return true
        }
        return _uiState.value.localSwitches.size < numberOfSwitchesLimit
    }

    /**
     * Sets up continuous observation of switch events
     */
    private fun observeSwitches(context: Context) {
        viewModelScope.launch {
            // Start loading state
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Create a flow for local switches that listens to store updates
            val localSwitchesFlow = callbackFlow {
                // Initial emission with current switches
                trySend(store.getSwitchEvents())

                // Set up broadcast receiver for updates
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == SwitchEventStore.EVENTS_UPDATED) {
                            trySend(store.getSwitchEvents())
                        }
                    }
                }

                // Register receiver
                LocalBroadcastManager.getInstance(context)
                    .registerReceiver(receiver, IntentFilter(SwitchEventStore.EVENTS_UPDATED))

                // Clean up when flow is cancelled
                awaitClose {
                    // Unregister receiver
                    LocalBroadcastManager.getInstance(context)
                        .unregisterReceiver(receiver)
                }
            }

            // Create a flow for remote switches
            val remoteSwitchesFlow = flow {
                store.fetchAvailableSwitches()
                    .onSuccess { emit(it) }
                    .onFailure { emit(emptyList()) }
            }

            // Combine both flows
            combine(
                localSwitchesFlow,
                remoteSwitchesFlow
            ) { localSwitches, remoteSwitches ->
                _uiState.value = _uiState.value.copy(
                    localSwitches = localSwitches,
                    remoteSwitches = remoteSwitches,
                    isLoading = false
                )
            }.collect()
        }
    }

    fun showProAlert() {
        _uiState.value = _uiState.value.copy(
            showProAlert = true
        )
    }

    fun hideProAlert() {
        _uiState.value = _uiState.value.copy(
            showProAlert = false
        )
    }

    /**
     * Imports a single remote switch.
     */
    fun importSwitch(remoteSwitch: RemoteSwitchInfo, context: Context) {
        viewModelScope.launch {
            if (!isAnotherSwitchAllowed()) {
                showProAlert()
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                importingSwitch = remoteSwitch.code
            )

            store.importSwitch(remoteSwitch.code, context)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        remoteSwitches = _uiState.value.remoteSwitches.filterNot { it.code == remoteSwitch.code },
                        importingSwitch = null
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        importingSwitch = null
                    )
                }
        }
    }

    /**
     * Deletes a remote switch.
     */
    fun deleteRemoteSwitch(remoteSwitch: RemoteSwitchInfo, context: Context) {
        viewModelScope.launch {
            store.removeRemote(remoteSwitch.code, context)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        importingSwitch = null
                    )
                }
        }
    }
}

/**
 * Represents the UI state for the Switches screen.
 */
data class SwitchesUiState(
    val localSwitches: Set<SwitchEvent> = emptySet(),
    val remoteSwitches: List<RemoteSwitchInfo> = emptyList(),
    val isLoading: Boolean = false,
    val shouldLimitSwitches: Boolean = false,
    val showProAlert: Boolean = false,
    val importingSwitch: String? = null
)