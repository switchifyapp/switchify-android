package com.enaboapps.switchify.screens.pc.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.remote.PcLiveTypingModel
import com.enaboapps.switchify.pc.remote.PcRemoteConnection
import com.enaboapps.switchify.pc.remote.PcRemotePreferences
import com.enaboapps.switchify.pc.remote.PcRemoteSession
import com.enaboapps.switchify.pc.remote.PcRepeatSwitchStop
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.pc.remote.PcTypingMode
import com.enaboapps.switchify.pc.remote.actions.PcActionContext
import com.enaboapps.switchify.pc.remote.actions.PcActionDefinition
import com.enaboapps.switchify.pc.remote.actions.PcActionRuntime
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutEditBlocking
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutEditorState
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSaveRequest
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSections
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutStore
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSurface
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.pc.transport.PcUnsubscribe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PcRemoteSessionHolder(val desktopId: String, val session: PcRemoteSession, val liveTyping: PcLiveTypingModel)

class PcRemoteViewModel(
    private val manager: PcRemoteConnection,
    val preferences: PcRemotePreferences,
    val layouts: PcLayoutStore,
    private val switchStop: PcRepeatSwitchStop,
    private val sessionScope: CoroutineScope
) : ViewModel() {
    val connection: StateFlow<PcConnectionState> = manager.state

    private val _holder = MutableStateFlow<PcRemoteSessionHolder?>(null)
    val holder: StateFlow<PcRemoteSessionHolder?> = _holder.asStateFlow()

    private val _physicalSwitchStopAvailable = MutableStateFlow(switchStop.isAvailable())
    val physicalSwitchStopAvailable: StateFlow<Boolean> = _physicalSwitchStopAvailable.asStateFlow()

    private val _editingLayout = MutableStateFlow(false)
    val editingLayout: StateFlow<Boolean> = _editingLayout.asStateFlow()

    private val _layoutEditor = MutableStateFlow<PcLayoutEditorSession?>(null)
    val layoutEditor: StateFlow<PcLayoutEditorSession?> = _layoutEditor.asStateFlow()

    private var openingLayoutEditor = false
    private var lastDesktopId: String? = null

    private var cleanupRegistration: PcUnsubscribe? = null

    init {
        viewModelScope.launch {
            manager.state
                .map { (it as? PcConnectionState.Connected)?.desktop?.desktopId }
                .distinctUntilChanged()
                .collect(::replaceSession)
        }
    }

    fun onVisible() {
        refreshPhysicalSwitchStop()
    }

    fun onHidden(changingConfigurations: Boolean) {
        if (!changingConfigurations) releaseHeldInput()
    }

    private fun releaseHeldInput() {
        val session = _holder.value?.session ?: return
        sessionScope.launch(start = CoroutineStart.UNDISPATCHED) { session.cleanup() }
    }

    fun refreshPhysicalSwitchStop() {
        _physicalSwitchStopAvailable.value = switchStop.isAvailable()
    }

    fun retry() {
        viewModelScope.launch { manager.connectPreferred() }
    }

    fun selectSurface(surface: PcRemoteSurface) {
        if (!surface.usesLayouts) {
            _editingLayout.value = false
            releaseHeldInput()
        }
        preferences.setSurface(surface)
    }

    fun selectTypingMode(mode: PcTypingMode) {
        if (mode == PcTypingMode.Draft) _holder.value?.session?.let { session -> launchAction { session.closeStream() } }
        preferences.setTypingMode(mode)
    }

    fun setDraft(text: String) = preferences.setDraft(text)

    fun toggleLayoutEditing() {
        _editingLayout.value = !_editingLayout.value
    }

    fun layoutEditingBlocked(): Boolean {
        val holder = _holder.value ?: return false
        return PcLayoutEditBlocking.block(
            holder.session.state.value,
            preferences.surface.value,
            holder.liveTyping.submitting.value
        ) != null
    }

    fun openLayoutEditor(
        surface: PcLayoutSurface,
        section: String,
        controls: List<PcResolvedAction>,
        width: Float,
        fontScale: Float
    ) {
        if (openingLayoutEditor || _layoutEditor.value != null) return
        val definition = PcLayoutSections.get(surface, section) ?: return
        openingLayoutEditor = true
        viewModelScope.launch {
            try {
                layouts.load()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            } finally {
                openingLayoutEditor = false
            }
            if (!_editingLayout.value || layoutEditingBlocked() || _layoutEditor.value != null) return@launch
            val defaults = PcLayoutSections.sectionDefault(definition, width, fontScale)
            val stored = layouts.layouts.value[surface]?.get(section)
            val customized = stored != null && PcLayoutSections.isValidSectionLayout(surface, section, stored)
            _layoutEditor.value = PcLayoutEditorSession(
                section = section,
                titleRes = definition.titleRes,
                controls = controls,
                state = PcLayoutEditorState(
                    surface = surface,
                    initial = if (customized) stored else defaults,
                    defaultLayout = defaults,
                    initiallyCustomized = customized
                )
            )
        }
    }

    fun refreshLayoutEditorControls(surface: PcLayoutSurface, section: String, controls: List<PcResolvedAction>) {
        _layoutEditor.update { session ->
            if (session != null && session.state.surface == surface && session.section == section && session.controls != controls) {
                session.copy(controls = controls)
            } else {
                session
            }
        }
    }

    fun editLayout(transform: (PcLayoutEditorState) -> PcLayoutEditorState) {
        _layoutEditor.update { session ->
            if (session == null || session.saving) return@update session
            val next = transform(session.state)
            val changed = next.draft !== session.state.draft || next.reset != session.state.reset
            session.copy(state = next, failed = session.failed && !changed)
        }
    }

    fun requestLayoutConfirmation(confirmation: PcLayoutConfirmation?) {
        _layoutEditor.update { session ->
            if (session == null || (session.saving && confirmation != null)) session else session.copy(confirmation = confirmation)
        }
    }

    fun confirmLayout() {
        val session = _layoutEditor.value ?: return
        val confirmation = session.confirmation ?: return
        _layoutEditor.value = session.copy(confirmation = null)
        when (confirmation) {
            PcLayoutConfirmation.Discard -> closeLayoutEditor()
            PcLayoutConfirmation.Reset -> editLayout { it.resetToDefault() }
            is PcLayoutConfirmation.RemoveTrack -> editLayout { it.resize(confirmation.axis, confirmation.index, false) }
        }
    }

    fun dismissLayoutEditor() {
        val session = _layoutEditor.value ?: return
        when {
            session.saving -> Unit
            session.state.pickerCell != null -> editLayout { it.closePicker() }
            session.state.dirty -> requestLayoutConfirmation(PcLayoutConfirmation.Discard)
            else -> closeLayoutEditor()
        }
    }

    fun closeLayoutEditor() {
        _layoutEditor.update { session -> if (session?.saving == true) session else null }
    }

    fun saveLayoutEditor() {
        val session = _layoutEditor.value ?: return
        if (session.saving) return
        _layoutEditor.value = session.copy(saving = true, failed = false)
        val request = session.state.saveRequest()
        viewModelScope.launch {
            try {
                if (request is PcLayoutSaveRequest.Save) layouts.save(session.state.surface, session.section, request.layout)
                _layoutEditor.update { current -> if (current?.saving == true) null else current }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _layoutEditor.update { current ->
                    if (current?.saving == true) current.copy(saving = false, failed = true, failureCount = current.failureCount + 1) else current
                }
            }
        }
    }

    fun stopRepeat() {
        val session = _holder.value?.session ?: return
        launchAction { session.stopRepeat() }
    }

    fun perform(action: PcActionDefinition, context: () -> PcActionContext?) {
        launchAction {
            val current = context() ?: return@launchAction
            if (current.session !== _holder.value?.session) return@launchAction
            PcActionRuntime.execute(action, current)
        }
    }

    fun submitLive() {
        val live = _holder.value?.liveTyping ?: return
        launchAction { live.submit() }
    }

    suspend fun listSaved(): List<PcSavedPc> = try {
        manager.listSaved()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyList()
    }

    fun switchTo(pc: PcSavedPc) {
        viewModelScope.launch { manager.switchSaved(pc) }
    }

    private fun replaceSession(desktopId: String?) {
        if (desktopId != null && lastDesktopId != null && desktopId != lastDesktopId) _editingLayout.value = false
        if (desktopId != null) lastDesktopId = desktopId
        cleanupRegistration?.unsubscribe()
        cleanupRegistration = null
        _holder.value?.let(::retire)
        if (desktopId == null) {
            _holder.value = null
            return
        }
        val session = PcRemoteSession(
            sender = { command, responseMode -> manager.send(command, responseMode) },
            profileProvider = {
                (manager.state.value as? PcConnectionState.Connected)
                    ?.takeIf { it.desktop.desktopId == desktopId }
                    ?.profile
            },
            scope = sessionScope,
            switchStop = switchStop
        )
        cleanupRegistration = manager.registerCleanup {
            withContext(Dispatchers.Main.immediate) { session.cleanup() }
        }
        _holder.value = PcRemoteSessionHolder(desktopId, session, PcLiveTypingModel(session, sessionScope))
    }

    private fun retire(holder: PcRemoteSessionHolder) {
        sessionScope.launch {
            try {
                holder.session.close()
            } finally {
                holder.session.dispose()
            }
        }
    }

    private fun launchAction(block: suspend () -> Unit) {
        sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        cleanupRegistration?.unsubscribe()
        cleanupRegistration = null
        _holder.value?.let(::retire)
        _holder.value = null
    }
}
