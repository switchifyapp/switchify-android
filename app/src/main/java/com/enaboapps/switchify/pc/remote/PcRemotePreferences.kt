package com.enaboapps.switchify.pc.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PcRemoteSurface(val storageKey: String) {
    Mouse("mouse"),
    Typing("typing"),
    Window("window");

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

class SharedPcRemotePreferences private constructor(
    private val preferences: SharedPreferences
) : InMemoryPcRemotePreferences(
    PcRemoteSurface.fromStorageKey(preferences.getString(SURFACE_KEY, null)),
    PcTypingMode.fromStorageKey(preferences.getString(TYPING_MODE_KEY, null))
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    )

    override fun setSurface(surface: PcRemoteSurface) {
        super.setSurface(surface)
        preferences.edit { putString(SURFACE_KEY, surface.storageKey) }
    }

    override fun setTypingMode(mode: PcTypingMode) {
        super.setTypingMode(mode)
        preferences.edit { putString(TYPING_MODE_KEY, mode.storageKey) }
    }

    private companion object {
        const val FILE_NAME = "switchify_pc_remote"
        const val SURFACE_KEY = "surface"
        const val TYPING_MODE_KEY = "typingMode"
    }
}
