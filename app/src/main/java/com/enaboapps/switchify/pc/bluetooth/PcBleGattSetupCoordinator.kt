package com.enaboapps.switchify.pc.bluetooth

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class PcBleGattSetupCoordinator(
    private val mtuTimeoutMs: Long = PC_BLE_MTU_TIMEOUT_MS
) {
    private val setupMutex = Mutex()
    private val mtuRequestAttempted = AtomicBoolean(false)
    private val mtuResult = CompletableDeferred<Boolean>()
    private var serviceDiscoveryResult: Boolean? = null

    suspend fun startServiceDiscovery(
        requestMtu: (Int) -> Boolean,
        discoverServices: () -> Boolean
    ): Boolean = setupMutex.withLock {
        serviceDiscoveryResult?.let { return@withLock it }

        if (mtuRequestAttempted.compareAndSet(false, true)) {
            val requestStarted = runCatching { requestMtu(PC_BLE_TARGET_MTU) }.getOrDefault(false)
            if (requestStarted) {
                withTimeoutOrNull(mtuTimeoutMs) { mtuResult.await() }
            }
        }

        discoverServices().also { serviceDiscoveryResult = it }
    }

    fun onMtuChanged(succeeded: Boolean) {
        if (mtuRequestAttempted.get()) {
            mtuResult.complete(succeeded)
        }
    }
}

internal const val PC_BLE_TARGET_MTU = 517
internal const val PC_BLE_MTU_TIMEOUT_MS = 5_000L
