package com.enaboapps.switchify.service.remotebridge

enum class ScanningHold {
    Forwarding,
    PcRepeat
}

class ScanningHolds {
    private val holds = mutableSetOf<ScanningHold>()

    fun isHeld(): Boolean = holds.isNotEmpty()

    fun update(hold: ScanningHold, held: Boolean): Boolean? {
        val wasHeld = isHeld()
        if (held) holds += hold else holds -= hold
        val nowHeld = isHeld()
        return if (wasHeld == nowHeld) null else nowHeld
    }
}
