package com.enaboapps.switchify.pc

data class PcPointerMovementProfile(
    val displayId: String,
    val scaleFactor: Double,
    val bounds: PcPointerBounds,
    val maxDelta: Int,
    val recommendedDeltas: PcPointerDeltas,
    val capabilities: PcPointerCapabilities = PcPointerCapabilities()
)

data class PcPointerCapabilities(
    val noAckMouseMove: Boolean = false,
    val noAckCommands: Set<String> = emptySet(),
    val supportedCommands: Set<String> = emptySet(),
    val mouseRepeat: PcMouseRepeatCapabilities = PcMouseRepeatCapabilities(),
    val pointerSpeed: PcPointerSpeedCapabilities = PcPointerSpeedCapabilities()
)

data class PcPointerSpeedCapabilities(
    val supported: Boolean = false,
    val setSupported: Boolean = false,
    val scalePercent: Double = 100.0,
    val minScalePercent: Double = 5.0,
    val maxScalePercent: Double = 225.0,
    val stepPercent: Double = 5.0,
    val baseMoveDelta: Int = 128,
    val effectiveMoveDelta: Int = 128
)

data class PcMouseRepeatCapabilities(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val intervalMs: Long = 250L,
    val minIntervalMs: Long = 100L,
    val maxIntervalMs: Long = 2000L
)

data class PcPointerBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class PcPointerDeltas(
    val small: Int,
    val medium: Int,
    val large: Int
)

fun PcPointerMovementProfile.supportsTextStreams(): Boolean {
    return capabilities.supportedCommands.containsAll(
        setOf(
            "keyboard.textStream.open",
            "keyboard.textStream.chunk",
            "keyboard.textStream.key",
            "keyboard.textStream.close"
        )
    )
}

fun PcPointerMovementProfile.supportsNoAckTextStreamChunks(): Boolean {
    return capabilities.noAckCommands.contains("keyboard.textStream.chunk")
}

fun PcPointerMovementProfile.supportsModifierToggle(): Boolean {
    return capabilities.supportedCommands.containsAll(
        setOf(
            "keyboard.modifierDown",
            "keyboard.modifierUp"
        )
    )
}

fun PcPointerMovementProfile.pointerMoveStep(): Int {
    val speed = capabilities.pointerSpeed
    val candidate = if (speed.supported) speed.baseMoveDelta else recommendedDeltas.medium
    return candidate.coerceIn(1, maxDelta)
}
