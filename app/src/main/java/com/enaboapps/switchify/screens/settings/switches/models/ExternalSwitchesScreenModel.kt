package com.enaboapps.switchify.screens.settings.switches.models

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ExternalSwitchesScreenModel : ViewModel() {
    private val store = SwitchEventStore.getInstance()
    private val _uiState = MutableStateFlow(ExternalSwitchesUiState())
    val uiState: StateFlow<ExternalSwitchesUiState> = _uiState

    private val numberOfSwitchesLimit = 3

    fun setup(context: Context) {
        observeExternalSwitches(context)
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
        // Count all switches, not just external ones, for the total limit
        val allSwitches = store.getSwitchEvents()
        return allSwitches.size < numberOfSwitchesLimit
    }

    private fun observeExternalSwitches(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val externalSwitchesFlow = callbackFlow {
                trySend(store.getSwitchEvents().filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL })

                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == SwitchEventStore.EVENTS_UPDATED) {
                            val externalSwitches = store.getSwitchEvents()
                                .filter { it.type == SWITCH_EVENT_TYPE_EXTERNAL }
                            trySend(externalSwitches)
                        }
                    }
                }

                LocalBroadcastManager.getInstance(context)
                    .registerReceiver(receiver, IntentFilter(SwitchEventStore.EVENTS_UPDATED))

                awaitClose {
                    LocalBroadcastManager.getInstance(context)
                        .unregisterReceiver(receiver)
                }
            }

            externalSwitchesFlow.collect { externalSwitches ->
                _uiState.value = _uiState.value.copy(
                    externalSwitches = externalSwitches,
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

data class ExternalSwitchesUiState(
    val externalSwitches: List<SwitchEvent> = emptyList(),
    val isLoading: Boolean = false,
    val shouldLimitSwitches: Boolean = false,
    val showProAlert: Boolean = false
)