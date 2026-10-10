package com.enaboapps.switchify.pc.protocol

import java.util.UUID
import kotlin.math.abs

fun interface PcClock {
    fun nowMillis(): Long

    companion object {
        val System = PcClock { java.lang.System.currentTimeMillis() }
    }
}

fun interface PcIdGenerator {
    fun nextId(): String

    companion object {
        val Request = PcIdGenerator { "android-${UUID.randomUUID()}" }
        val Frame = PcIdGenerator { UUID.randomUUID().toString() }
        val Nonce = PcIdGenerator { UUID.randomUUID().toString() }
    }
}

object PcVerificationCode {
    fun derive(desktopId: String, deviceId: String, requestNonce: String): String {
        val canonical = "$desktopId\n$deviceId\n$requestNonce"
        var hash = FNV_OFFSET_BASIS
        canonical.forEach { unit ->
            hash = hash xor unit.code
            hash *= FNV_PRIME
        }
        return (abs(hash.toLong()) % 1_000_000).toString().padStart(6, '0')
    }

    private const val FNV_OFFSET_BASIS = -0x7ee3623b
    private const val FNV_PRIME = 16_777_619
}
