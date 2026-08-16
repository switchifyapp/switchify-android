package com.enaboapps.switchify.screens.pcswitchforwarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchForwardingProfileStore
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
import com.enaboapps.switchify.pc.PcServiceSwitcherConnectionHost
import com.enaboapps.switchify.pc.PcSwitcherCoordinator
import com.enaboapps.switchify.pc.PcSwitcherConnectionHost
import com.enaboapps.switchify.pc.PcSwitcherUiState
import com.enaboapps.switchify.pc.selectPcSwitchForwardingProfile
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchCatalogResult
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchForwardingChooserHost
import com.enaboapps.switchify.service.pcswitchforwarding.PcSwitchForwardingStartResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class PcSwitchNoticeSeverity {
    Information,
    Warning,
    Error
}

internal data class PcSwitchNotice(
    val text: String,
    val severity: PcSwitchNoticeSeverity
)

internal sealed interface PcSwitchChooserState {
    data object Loading : PcSwitchChooserState

    data class Ready(
        val catalog: PcSwitchProfileCatalog,
        val selected: PcSwitchProfileSummary,
        val notice: PcSwitchNotice? = null
    ) : PcSwitchChooserState

    data object Empty : PcSwitchChooserState

    data class Error(val message: String) : PcSwitchChooserState

    data class Starting(
        val catalog: PcSwitchProfileCatalog,
        val selected: PcSwitchProfileSummary
    ) : PcSwitchChooserState
}

internal class PcSwitchForwardingChooserViewModel(
    private val host: PcSwitchForwardingChooserHost,
    private val rememberedProfileId: (String) -> String?,
    private val rememberProfile: (String, String) -> Unit,
    private val message: (Int, List<Any>) -> String,
    switcherConnectionHost: PcSwitcherConnectionHost =
        PcServiceSwitcherConnectionHost { null }
) : ViewModel() {
    constructor(context: Context, host: PcSwitchForwardingChooserHost) : this(
        host = host,
        rememberedProfileId = PcSwitchForwardingProfileStore(context)::rememberedProfileId,
        rememberProfile = PcSwitchForwardingProfileStore(context)::rememberProfile,
        message = { resourceId, arguments ->
            context.getString(resourceId, *arguments.toTypedArray())
        },
        switcherConnectionHost = PcServiceSwitcherConnectionHost {
            ServiceCore.getPcServiceConnectionController()
        }
    )

    private val _state = MutableStateFlow<PcSwitchChooserState>(PcSwitchChooserState.Loading)
    val state: StateFlow<PcSwitchChooserState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var startJob: Job? = null
    private val pcSwitcher = PcSwitcherCoordinator(
        host = switcherConnectionHost,
        scope = viewModelScope,
        beforeOpen = ::prepareForPcSwitcher,
        afterSwitch = ::load
    )
    val pcSwitcherState: StateFlow<PcSwitcherUiState> = pcSwitcher.state

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun openPcSwitcher() {
        pcSwitcher.open()
    }

    fun dismissPcSwitcher() {
        pcSwitcher.dismiss()
    }

    fun refreshPcSwitcher() {
        pcSwitcher.refresh()
    }

    fun switchToPc(desktopId: String) {
        pcSwitcher.switchTo(desktopId)
    }

    fun cancelPcSwitchPairing() {
        pcSwitcher.cancelPairing()
    }

    fun select(profile: PcSwitchProfileSummary) {
        val ready = _state.value as? PcSwitchChooserState.Ready ?: return
        if (ready.catalog.profiles.none { it.id == profile.id }) return
        _state.value = ready.copy(selected = profile, notice = null)
    }

    fun start() {
        val ready = _state.value as? PcSwitchChooserState.Ready ?: return
        if (startJob?.isActive == true) return
        _state.value = PcSwitchChooserState.Starting(ready.catalog, ready.selected)
        startJob = viewModelScope.launch {
            _state.value = when (
                val result = host.start(
                    ready.selected,
                    ready.catalog.usesLegacyGridProtocol
                )
            ) {
                PcSwitchForwardingStartResult.Started -> {
                    rememberProfile(host.currentPcId(), ready.selected.id)
                    ready.copy(notice = null)
                }
                PcSwitchForwardingStartResult.NoExternalSwitches -> {
                    ready.copy(
                        notice = PcSwitchNotice(
                            text = message(
                                R.string.pc_switch_forwarding_no_external_switches,
                                emptyList()
                            ),
                            severity = PcSwitchNoticeSeverity.Warning
                        )
                    )
                }
                PcSwitchForwardingStartResult.UnsupportedPc -> {
                    ready.copy(
                        notice = PcSwitchNotice(
                            text = message(
                                R.string.pc_switch_forwarding_pc_unsupported,
                                emptyList()
                            ),
                            severity = PcSwitchNoticeSeverity.Error
                        )
                    )
                }
                PcSwitchForwardingStartResult.ProfileChanged -> {
                    PcSwitchChooserState.Error(
                        message(R.string.pc_switch_profile_changed, emptyList())
                    )
                }
                is PcSwitchForwardingStartResult.Failed -> {
                    ready.copy(
                        notice = PcSwitchNotice(
                            result.message,
                            PcSwitchNoticeSeverity.Error
                        )
                    )
                }
            }
        }
    }

    private fun load() {
        startJob?.cancel()
        loadJob?.cancel()
        _state.value = PcSwitchChooserState.Loading
        loadJob = viewModelScope.launch {
            _state.value = when (val result = host.loadProfileCatalog()) {
                is PcSwitchCatalogResult.Loaded -> {
                    val selection = selectPcSwitchForwardingProfile(
                        result.catalog.profiles,
                        rememberedProfileId(host.currentPcId())
                    )
                    val selectedProfile = selection.profile
                    if (selectedProfile == null) {
                        PcSwitchChooserState.Empty
                    } else {
                        PcSwitchChooserState.Ready(
                            catalog = result.catalog,
                            selected = selectedProfile,
                            notice = selection.fallbackProfileName?.let { fallbackName ->
                                PcSwitchNotice(
                                    text = message(
                                        R.string.pc_switch_previous_profile_unavailable,
                                        listOf(fallbackName)
                                    ),
                                    severity = PcSwitchNoticeSeverity.Warning
                                )
                            }
                        )
                    }
                }
                is PcSwitchCatalogResult.Failed ->
                    PcSwitchChooserState.Error(result.message)
                PcSwitchCatalogResult.Unsupported ->
                    PcSwitchChooserState.Error(
                        message(R.string.pc_switch_forwarding_pc_unsupported, emptyList())
                    )
            }
        }
    }

    private suspend fun prepareForPcSwitcher() {
        val starting = _state.value as? PcSwitchChooserState.Starting
        startJob?.cancelAndJoin()
        startJob = null
        if (starting != null) {
            _state.value = PcSwitchChooserState.Ready(
                catalog = starting.catalog,
                selected = starting.selected
            )
        }
        host.prepareForPcSwitcher()
    }

    override fun onCleared() {
        pcSwitcher.dispose()
    }
}
