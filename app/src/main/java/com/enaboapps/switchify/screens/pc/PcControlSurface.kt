package com.enaboapps.switchify.screens.pc

import android.content.Context
import androidx.annotation.StringRes
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager

enum class PcControlSurface(
    val preferenceValue: String,
    @param:StringRes val labelResId: Int
) {
    Mouse("mouse", R.string.pc_control_surface_mouse),
    Typing("typing", R.string.pc_control_surface_typing),
    Window("window", R.string.pc_control_surface_window);

    companion object {
        fun fromPreferenceValue(value: String?): PcControlSurface {
            return entries.firstOrNull { it.preferenceValue == value } ?: Mouse
        }
    }
}

interface PcControlSurfaceStore {
    fun getSelectedSurface(): PcControlSurface
    fun setSelectedSurface(surface: PcControlSurface)
}

class PcControlSurfacePreferenceStore(context: Context) : PcControlSurfaceStore {
    private val preferenceManager = PreferenceManager(context.applicationContext)

    override fun getSelectedSurface(): PcControlSurface {
        return PcControlSurface.fromPreferenceValue(
            preferenceManager.getStringValue(PreferenceManager.PREFERENCE_KEY_PC_CONTROL_SURFACE)
        )
    }

    override fun setSelectedSurface(surface: PcControlSurface) {
        preferenceManager.setStringValue(
            PreferenceManager.PREFERENCE_KEY_PC_CONTROL_SURFACE,
            surface.preferenceValue
        )
    }
}

interface PcTypingDraftStore {
    fun getDraft(): String
    fun setDraft(text: String)
    fun clearDraft()
}

class PcTypingDraftPreferenceStore(context: Context) : PcTypingDraftStore {
    private val preferenceManager = PreferenceManager(context.applicationContext)

    override fun getDraft(): String {
        return preferenceManager.getStringValue(PreferenceManager.PREFERENCE_KEY_PC_TYPING_DRAFT)
    }

    override fun setDraft(text: String) {
        preferenceManager.setStringValue(
            PreferenceManager.PREFERENCE_KEY_PC_TYPING_DRAFT,
            text
        )
    }

    override fun clearDraft() {
        preferenceManager.setStringValue(
            PreferenceManager.PREFERENCE_KEY_PC_TYPING_DRAFT,
            ""
        )
    }
}
