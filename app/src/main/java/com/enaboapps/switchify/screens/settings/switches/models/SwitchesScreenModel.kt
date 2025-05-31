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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Switches screen, handling switch events.
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

            // Collect local switches flow
            localSwitchesFlow.collect { localSwitches ->
                _uiState.value = _uiState.value.copy(
                    localSwitches = localSwitches,
                    isLoading = false
                )
            }
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

}

/**
 * Represents the UI state for the Switches screen.
 */
data class SwitchesUiState(
    val localSwitches: Set<SwitchEvent> = emptySet(),
    val isLoading: Boolean = false,
    val shouldLimitSwitches: Boolean = false,
    val showProAlert: Boolean = false
)