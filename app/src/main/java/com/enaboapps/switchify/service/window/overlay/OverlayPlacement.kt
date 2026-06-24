package com.enaboapps.switchify.service.window.overlay

sealed class OverlayPlacement {
    data class Bounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : OverlayPlacement()

    data class WrapAt(
        val x: Int,
        val y: Int
    ) : OverlayPlacement()

    data class BottomCentered(
        val margins: Int = 0
    ) : OverlayPlacement()

    data class TopCentered(
        val margins: Int = 0
    ) : OverlayPlacement()

    data object Centered : OverlayPlacement()
}
