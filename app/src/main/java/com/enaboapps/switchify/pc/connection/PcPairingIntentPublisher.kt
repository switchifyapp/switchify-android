package com.enaboapps.switchify.pc.connection

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface PcPairingIntentPublisher {
    suspend fun publish(desktopId: String, deviceId: String, nonce: String): Boolean

    companion object {
        val None = PcPairingIntentPublisher { _, _, _ -> false }
    }
}

class AccountPcPairingIntentPublisher(
    private val isSignedIn: suspend () -> Boolean,
    private val createIntent: suspend (desktopId: String, deviceId: String, nonce: String) -> Unit
) : PcPairingIntentPublisher {
    override suspend fun publish(desktopId: String, deviceId: String, nonce: String): Boolean = try {
        if (isSignedIn()) {
            createIntent(desktopId, deviceId, nonce)
            true
        } else {
            false
        }
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        false
    }
}
