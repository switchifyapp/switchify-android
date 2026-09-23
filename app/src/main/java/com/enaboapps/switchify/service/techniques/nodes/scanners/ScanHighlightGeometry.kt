package com.enaboapps.switchify.service.techniques.nodes.scanners

data class ScanHighlightBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun interpolate(next: ScanHighlightBounds, fraction: Float): ScanHighlightBounds {
        val progress = fraction.coerceIn(0f, 1f)
        fun between(from: Float, to: Float) = from + (to - from) * progress
        return ScanHighlightBounds(between(x, next.x), between(y, next.y),
            between(width, next.width), between(height, next.height))
    }

    val isUsable: Boolean get() = x.isFinite() && y.isFinite() &&
        width.isFinite() && height.isFinite() && width > 0f && height > 0f
}
