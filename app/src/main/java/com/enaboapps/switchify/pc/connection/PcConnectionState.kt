package com.enaboapps.switchify.pc.connection

import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.pc.transport.PcDiscoveredDesktop

enum class PcProfileStatus {
    Ready,
    Recovering,
    Unavailable
}

enum class PcConnectionFailure {
    CouldNotConnect,
    NotFoundNearby,
    SavedAccessLoadFailed,
    SavedAccessUnavailable,
    SavedAccessInvalid,
    AccessRevoked,
    PairingRejected,
    PairingExpired,
    ConnectionLost,
    DiscoveryFailed,
    UnpairFailed
}

sealed class PcConnectionState {
    data class Idle(val saved: List<PcSavedPc>) : PcConnectionState()
    data class PermissionDenied(val saved: List<PcSavedPc>) : PcConnectionState()
    data class BluetoothOff(val saved: List<PcSavedPc>) : PcConnectionState()
    data class Unsupported(val saved: List<PcSavedPc>) : PcConnectionState()
    data class LocationOff(val saved: List<PcSavedPc>) : PcConnectionState()
    data class Scanning(val saved: List<PcSavedPc>, val discovered: List<PcDiscoveredDesktop>) : PcConnectionState()
    data class Connecting(val desktop: PcDiscoveredDesktop) : PcConnectionState()
    data class Reconnecting(val desktop: PcDiscoveredDesktop, val attempt: Int) : PcConnectionState()
    data class Pairing(val desktop: PcDiscoveredDesktop, val verificationCode: String) : PcConnectionState()
    data class Connected(
        val desktop: PcDiscoveredDesktop,
        val profile: PcPointerProfile?,
        val profileStatus: PcProfileStatus
    ) : PcConnectionState()
    data class Failed(val failure: PcConnectionFailure, val saved: List<PcSavedPc>) : PcConnectionState()

    val savedPcs: List<PcSavedPc>?
        get() = when (this) {
            is Idle -> saved
            is PermissionDenied -> saved
            is BluetoothOff -> saved
            is Unsupported -> saved
            is LocationOff -> saved
            is Scanning -> saved
            is Failed -> saved
            else -> null
        }

    val activeDesktop: PcDiscoveredDesktop?
        get() = when (this) {
            is Connecting -> desktop
            is Reconnecting -> desktop
            is Pairing -> desktop
            is Connected -> desktop
            else -> null
        }

    fun withSaved(saved: List<PcSavedPc>): PcConnectionState = when (this) {
        is Idle -> copy(saved = saved)
        is PermissionDenied -> copy(saved = saved)
        is BluetoothOff -> copy(saved = saved)
        is Unsupported -> copy(saved = saved)
        is LocationOff -> copy(saved = saved)
        is Scanning -> copy(saved = saved)
        is Failed -> copy(saved = saved)
        else -> this
    }
}
