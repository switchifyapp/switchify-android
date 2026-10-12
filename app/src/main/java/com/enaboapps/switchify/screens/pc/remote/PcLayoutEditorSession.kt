package com.enaboapps.switchify.screens.pc.remote

import androidx.annotation.StringRes
import com.enaboapps.switchify.pc.remote.actions.PcResolvedAction
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutAxis
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutEditorState

sealed class PcLayoutConfirmation {
    data object Discard : PcLayoutConfirmation()
    data object Reset : PcLayoutConfirmation()
    data class RemoveTrack(val axis: PcLayoutAxis, val index: Int) : PcLayoutConfirmation()
}

data class PcLayoutEditorSession(
    val section: String,
    @StringRes val titleRes: Int,
    val controls: List<PcResolvedAction>,
    val state: PcLayoutEditorState,
    val saving: Boolean = false,
    val failed: Boolean = false,
    val failureCount: Int = 0,
    val confirmation: PcLayoutConfirmation? = null
)
