package com.enaboapps.switchify.pc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcConnectionLifecycleTest {
    @Test
    fun discoveryLeaseStartsAndStopsOnlyAtOuterEdges() {
        val discovery = RecordingDiscovery()
        val lease = PcDiscoveryLease(discovery)

        lease.acquire()
        lease.acquire()
        lease.release()
        lease.release()
        lease.release()

        assertEquals(1, discovery.starts)
        assertEquals(1, discovery.stops)
        assertFalse(lease.isActive)
    }

    @Test
    fun exclusiveAttemptCancelsPreviousJob() = runTest {
        val coordinator = PcConnectionAttemptCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val first = backgroundScope.launch {
            coordinator.runExclusive {
                firstStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        firstStarted.await()

        coordinator.runExclusive { Unit }
        advanceUntilIdle()

        assertTrue(first.isCancelled)
        assertFalse(coordinator.isActive)
    }

    @Test
    fun reconnectPolicyCapsAtLongestDelay() {
        assertEquals(500L, PcReconnectPolicy.delayForFailure(0))
        assertEquals(30_000L, PcReconnectPolicy.delayForFailure(100))
    }

    private class RecordingDiscovery : PcDiscovery {
        override val pcs = kotlinx.coroutines.flow.MutableStateFlow<List<DiscoveredPc>>(emptyList())
        override val status = kotlinx.coroutines.flow.MutableStateFlow(PcDiscoveryStatus.Idle)
        var starts = 0
        var stops = 0
        override fun startDiscovery() { starts++ }
        override fun stopDiscovery() { stops++ }
    }
}
