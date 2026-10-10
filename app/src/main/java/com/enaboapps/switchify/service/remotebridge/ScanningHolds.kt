package com.enaboapps.switchify.service.remotebridge

enum class ScanningHold {
    Forwarding,
    PcRepeat
}

interface ScanningHoldTarget {
    fun pauseScanning()
    fun resumeScanning()
    fun isPausedByUser(): Boolean
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

    fun apply(hold: ScanningHold, held: Boolean, target: ScanningHoldTarget?) {
        val nowHeld = update(hold, held) ?: return
        val scanning = target ?: return
        when {
            nowHeld -> scanning.pauseScanning()
            !scanning.isPausedByUser() -> scanning.resumeScanning()
        }
    }

    fun reset() = holds.clear()
}
