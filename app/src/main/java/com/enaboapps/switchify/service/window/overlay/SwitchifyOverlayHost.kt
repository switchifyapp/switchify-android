package com.enaboapps.switchify.service.window.overlay

import android.view.ViewGroup

interface SwitchifyOverlayHost {
    fun addView(
        target: OverlayTarget.Display,
        view: ViewGroup,
        placement: OverlayPlacement
    )

    fun removeView(
        target: OverlayTarget.Display,
        view: ViewGroup
    )

    fun removeView(
        target: OverlayTarget.Display,
        id: Int
    )
}
