package com.enaboapps.switchify.pc.connection

import kotlinx.coroutines.CancellationException

fun interface PcPairingIntentPublisher {
    suspend fun publish(desktopId: String, deviceId: String, nonce: String): Boolean

    companion object {
        val None = PcPairingIntentPublisher { _, _, _ -> false }
    }
}

class AccountPcPairingIntentPublisher(
    private val isSignedIn: () -> Boolean,
    private val createIntent: suspend (desktopId: String, deviceId: String, nonce: String) -> Unit
) : PcPairingIntentPublisher {
    override suspend fun publish(desktopId: String, deviceId: String, nonce: String): Boolean = try {
        if (isSignedIn()) {
            createIntent(desktopId, deviceId, nonce)
            true
        } else {
            false
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
}
