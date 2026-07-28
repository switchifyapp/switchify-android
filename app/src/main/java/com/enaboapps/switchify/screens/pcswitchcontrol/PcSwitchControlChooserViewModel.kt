package com.enaboapps.switchify.screens.pcswitchcontrol

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcSwitchProfileCatalog
import com.enaboapps.switchify.pc.PcSwitchProfilePreferenceStore
import com.enaboapps.switchify.pc.PcSwitchProfileSummary
import com.enaboapps.switchify.pc.selectPcSwitchProfile
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchCatalogResult
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlChooserHost
import com.enaboapps.switchify.service.pcswitchcontrol.PcSwitchControlStartResult
import kotlinx.coroutines.Job
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

internal class PcSwitchControlChooserViewModel(
    private val host: PcSwitchControlChooserHost,
    private val rememberedProfileId: (String) -> String?,
    private val rememberProfile: (String, String) -> Unit,
    private val message: (Int, List<Any>) -> String
) : ViewModel() {
    constructor(context: Context, host: PcSwitchControlChooserHost) : this(
        host = host,
        rememberedProfileId = PcSwitchProfilePreferenceStore(context)::rememberedProfileId,
        rememberProfile = PcSwitchProfilePreferenceStore(context)::rememberProfile,
        message = { resourceId, arguments ->
            context.getString(resourceId, *arguments.toTypedArray())
        }
    )

    private val _state = MutableStateFlow<PcSwitchChooserState>(PcSwitchChooserState.Loading)
    val state: StateFlow<PcSwitchChooserState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var startJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
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
                PcSwitchControlStartResult.Started -> {
                    rememberProfile(host.currentPcId(), ready.selected.id)
                    ready.copy(notice = null)
                }
                PcSwitchControlStartResult.NoExternalSwitches -> {
                    ready.copy(
                        notice = PcSwitchNotice(
                            text = message(
                                R.string.pc_switch_control_no_external_switches,
                                emptyList()
                            ),
                            severity = PcSwitchNoticeSeverity.Warning
                        )
                    )
                }
                PcSwitchControlStartResult.UnsupportedPc -> {
                    ready.copy(
                        notice = PcSwitchNotice(
                            text = message(
                                R.string.pc_switch_control_pc_unsupported,
                                emptyList()
                            ),
                            severity = PcSwitchNoticeSeverity.Error
                        )
                    )
                }
                PcSwitchControlStartResult.ProfileChanged -> {
                    PcSwitchChooserState.Error(
                        message(R.string.pc_switch_profile_changed, emptyList())
                    )
                }
                is PcSwitchControlStartResult.Failed -> {
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
                    val selection = selectPcSwitchProfile(
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
                        message(R.string.pc_switch_control_pc_unsupported, emptyList())
                    )
            }
        }
    }
}
