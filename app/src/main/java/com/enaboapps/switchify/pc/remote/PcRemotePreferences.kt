package com.enaboapps.switchify.pc.remote

import android.content.Context
import com.enaboapps.switchify.pc.control.PcPreferenceStorage
import com.enaboapps.switchify.pc.control.SharedPreferencesPcStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PcRemoteSurface(val storageKey: String) {
    Mouse("mouse"),
    Typing("typing"),
    Window("window"),
    Forwarding("forwarding");

    val usesLayouts: Boolean get() = this != Forwarding

    companion object {
        fun fromStorageKey(value: String?): PcRemoteSurface = entries.firstOrNull { it.storageKey == value } ?: Mouse
    }
}

enum class PcTypingMode(val storageKey: String) {
    Live("live"),
    Draft("draft");

    companion object {
        fun fromStorageKey(value: String?): PcTypingMode = if (value == Draft.storageKey) Draft else Live
    }
}

interface PcDraftStore {
    val draft: StateFlow<String>

    fun setDraft(text: String)
}

interface PcRemotePreferences : PcDraftStore {
    val surface: StateFlow<PcRemoteSurface>
    val typingMode: StateFlow<PcTypingMode>

    fun setSurface(surface: PcRemoteSurface)

    fun setTypingMode(mode: PcTypingMode)
}

open class InMemoryPcRemotePreferences(
    surface: PcRemoteSurface = PcRemoteSurface.Mouse,
    typingMode: PcTypingMode = PcTypingMode.Live
) : PcRemotePreferences {
    private val _surface = MutableStateFlow(surface)
    private val _typingMode = MutableStateFlow(typingMode)
    private val _draft = MutableStateFlow("")

    override val surface: StateFlow<PcRemoteSurface> = _surface.asStateFlow()
    override val typingMode: StateFlow<PcTypingMode> = _typingMode.asStateFlow()
    override val draft: StateFlow<String> = _draft.asStateFlow()

    override fun setSurface(surface: PcRemoteSurface) {
        _surface.value = surface
    }

    override fun setTypingMode(mode: PcTypingMode) {
        _typingMode.value = mode
    }

    override fun setDraft(text: String) {
        _draft.value = PcTextLimit.limit(text)
    }
}

class PersistedPcRemotePreferences(
    private val storage: PcPreferenceStorage
) : InMemoryPcRemotePreferences(
    PcRemoteSurface.fromStorageKey(storage.getString(SURFACE_KEY)),
    PcTypingMode.fromStorageKey(storage.getString(TYPING_MODE_KEY))
) {
    constructor(context: Context) : this(SharedPreferencesPcStorage(context, FILE_NAME))

    override fun setSurface(surface: PcRemoteSurface) {
        super.setSurface(surface)
        storage.putString(SURFACE_KEY, surface.storageKey)
    }

    override fun setTypingMode(mode: PcTypingMode) {
        super.setTypingMode(mode)
        storage.putString(TYPING_MODE_KEY, mode.storageKey)
    }

    companion object {
        const val FILE_NAME = "switchify_pc_remote"
        const val SURFACE_KEY = "surface"
        const val TYPING_MODE_KEY = "typingMode"
    }
}
