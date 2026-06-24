package com.enaboapps.switchify.service.window.overlay

import android.view.ViewGroup

interface OverlayHandle {
    val view: ViewGroup

    fun setVisible(visible: Boolean)

    fun release()
}
