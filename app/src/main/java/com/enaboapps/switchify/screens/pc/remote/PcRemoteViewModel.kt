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
import com.enaboapps.switchify.pc.remote.layouts.PcButtonLayout
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

    private var cleanupRegistration: PcUnsubscribe? = null

    init {
        viewModelScope.launch {
            manager.state
                .map { (it as? PcConnectionState.Connected)?.desktop?.desktopId }
                .distinctUntilChanged()
                .collect(::replaceSession)
        }
    }

    fun onStart() {
        refreshPhysicalSwitchStop()
        viewModelScope.launch { manager.connectPreferred() }
    }

    fun onStop(changingConfigurations: Boolean) {
        viewModelScope.launch { manager.cancelPreferredConnection() }
        if (changingConfigurations) return
        val session = _holder.value?.session ?: return
        sessionScope.launch(start = CoroutineStart.UNDISPATCHED) { session.cleanup() }
    }

    fun refreshPhysicalSwitchStop() {
        _physicalSwitchStopAvailable.value = switchStop.isAvailable()
    }

    fun retry() {
        viewModelScope.launch { manager.connectPreferred() }
    }

    fun selectSurface(surface: PcRemoteSurface) = preferences.setSurface(surface)

    fun selectTypingMode(mode: PcTypingMode) {
        if (mode == PcTypingMode.Draft) _holder.value?.session?.let { session -> launchAction { session.closeStream() } }
        preferences.setTypingMode(mode)
    }

    fun setDraft(text: String) = preferences.setDraft(text)

    fun toggleLayoutEditing() {
        _editingLayout.value = !_editingLayout.value
    }

    suspend fun loadLayouts() = layouts.load()

    suspend fun saveLayout(surface: PcLayoutSurface, section: String, layout: PcButtonLayout?) =
        layouts.save(surface, section, layout)

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
        _editingLayout.value = false
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
