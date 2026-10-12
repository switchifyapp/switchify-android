package com.enaboapps.switchify.pc.remote.layouts

import com.enaboapps.switchify.pc.remote.PcRemoteSessionState
import com.enaboapps.switchify.pc.remote.PcRemoteSurface

enum class PcLayoutEditBlock {
    SendingEnter,
    HeldInput
}

object PcLayoutEditBlocking {
    fun block(state: PcRemoteSessionState, surface: PcRemoteSurface, submittingEnter: Boolean): PcLayoutEditBlock? = when {
        surface == PcRemoteSurface.Typing && submittingEnter -> PcLayoutEditBlock.SendingEnter
        state.repeat != null || state.dragging || state.modifiers.isNotEmpty() -> PcLayoutEditBlock.HeldInput
        else -> null
    }
}
