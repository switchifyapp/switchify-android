package com.enaboapps.switchify.service.window

internal data class OverlayDebugIdentity(
    val stableId: String,
    val serviceEpoch: Long,
    val generation: Int,
    val sequence: Long
) {
    val diagnosticId: String
        get() = "$stableId:epoch=$serviceEpoch:generation=$generation:sequence=$sequence"
}
