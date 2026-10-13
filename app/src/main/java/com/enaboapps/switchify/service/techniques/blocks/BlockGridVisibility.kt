package com.enaboapps.switchify.service.techniques.blocks

internal enum class BlockGridPhase { HIDDEN, SCANNING, REFINING }

internal class BlockGridVisibility(
    private val post: (() -> Unit) -> Unit,
    private val render: (BlockGridPhase) -> Unit
) {
    private val lock = Any()
    private var phase = BlockGridPhase.HIDDEN
    private var generation = 0L

    fun show() = update { BlockGridPhase.SCANNING }

    fun holdForRefinePhase() = update { BlockGridPhase.REFINING }

    fun hide() = update {
        if (it == BlockGridPhase.REFINING) it else BlockGridPhase.HIDDEN
    }

    fun reset() = update { BlockGridPhase.HIDDEN }

    private fun update(next: (BlockGridPhase) -> BlockGridPhase) {
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
