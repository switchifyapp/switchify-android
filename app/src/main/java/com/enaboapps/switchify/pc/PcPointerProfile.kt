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
    val noAckMouseMove: Boolean = false
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
