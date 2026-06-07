package com.enaboapps.switchify.pc

import kotlin.math.abs

const val PAIRING_VERIFICATION_CODE_LENGTH = 6

fun createPairingVerificationCode(
    desktopId: String,
    deviceId: String,
    requestNonce: String
): String {
    val canonical = "$desktopId\n$deviceId\n$requestNonce"
    var hash = 0x811C9DC5.toInt()
    canonical.forEach { char ->
        hash = hash xor char.code
        hash *= 16_777_619
    }
    val value = abs(hash.toLong()) % 1_000_000
    return value.toString().padStart(PAIRING_VERIFICATION_CODE_LENGTH, '0')
}
