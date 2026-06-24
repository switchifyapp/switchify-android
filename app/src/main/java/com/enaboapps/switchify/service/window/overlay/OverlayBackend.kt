package com.enaboapps.switchify.service.window.overlay

import android.view.ViewGroup

interface OverlayBackend {
    fun attach(
        target: OverlayTarget,
        view: ViewGroup,
        placement: OverlayPlacement
    ): OverlayHandle?
}
