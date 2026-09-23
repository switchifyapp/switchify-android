package com.enaboapps.switchify.service.techniques.pointscan.blocks

internal enum class PointScanGridPhase { HIDDEN, SCANNING, LINE }

internal class PointScanGridVisibility(
    private val post: (() -> Unit) -> Unit,
    private val render: (PointScanGridPhase) -> Unit
) {
    private val lock = Any()
    private var phase = PointScanGridPhase.HIDDEN
    private var generation = 0L

    fun show() = update { PointScanGridPhase.SCANNING }

    fun holdForLinePhase() = update { PointScanGridPhase.LINE }

    fun hide() = update {
        if (it == PointScanGridPhase.LINE) it else PointScanGridPhase.HIDDEN
    }

    fun reset() = update { PointScanGridPhase.HIDDEN }

    private fun update(next: (PointScanGridPhase) -> PointScanGridPhase) {
        synchronized(lock) {
            phase = next(phase)
            val expectedGeneration = ++generation
            post {
                synchronized(lock) {
                    if (generation == expectedGeneration) render(phase)
                }
            }
        }
    }
}
