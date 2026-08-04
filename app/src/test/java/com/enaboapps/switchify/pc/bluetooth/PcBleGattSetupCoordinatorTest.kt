package com.enaboapps.switchify.pc.bluetooth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcBleGattSetupCoordinatorTest {
    @Test
    fun successfulMtuCallbackPrecedesServiceDiscovery() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = PcBleGattSetupCoordinator()

        val setup = async {
            coordinator.startServiceDiscovery(
                requestMtu = {
                    calls += "requestMtu:$it"
                    true
                },
                discoverServices = {
                    calls += "discoverServices"
                    true
                }
            )
        }
        runCurrent()

        assertEquals(listOf("requestMtu:517"), calls)
        assertFalse(setup.isCompleted)

        coordinator.onMtuChanged(true)

        assertTrue(setup.await())
        assertEquals(listOf("requestMtu:517", "discoverServices"), calls)
    }

    @Test
    fun failedMtuCallbackFallsBackToServiceDiscovery() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = PcBleGattSetupCoordinator()

        val setup = async {
            coordinator.startServiceDiscovery(
                requestMtu = {
                    calls += "requestMtu:$it"
                    true
                },
                discoverServices = {
                    calls += "discoverServices"
                    true
                }
            )
        }
        runCurrent()

        coordinator.onMtuChanged(false)

        assertTrue(setup.await())
        assertEquals(listOf("requestMtu:517", "discoverServices"), calls)
    }

    @Test
    fun rejectedMtuRequestDiscoversServicesImmediately() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = PcBleGattSetupCoordinator()

        val started = coordinator.startServiceDiscovery(
            requestMtu = {
                calls += "requestMtu:$it"
                false
            },
            discoverServices = {
                calls += "discoverServices"
                true
            }
        )

        assertTrue(started)
        assertEquals(listOf("requestMtu:517", "discoverServices"), calls)
    }

    @Test
    fun mtuRequestExceptionFallsBackToServiceDiscovery() = runTest {
        var discoveries = 0
        val coordinator = PcBleGattSetupCoordinator()

        val started = coordinator.startServiceDiscovery(
            requestMtu = { error("request failed") },
            discoverServices = {
                discoveries += 1
                true
            }
        )

        assertTrue(started)
        assertEquals(1, discoveries)
    }

    @Test
    fun mtuTimeoutFallsBackAfterFiveSeconds() = runTest {
        var discoveries = 0
        val coordinator = PcBleGattSetupCoordinator()

        val setup = async {
            coordinator.startServiceDiscovery(
                requestMtu = { true },
                discoverServices = {
                    discoveries += 1
                    true
                }
            )
        }
        runCurrent()

        advanceTimeBy(4_999)
        runCurrent()
        assertFalse(setup.isCompleted)
        assertEquals(0, discoveries)

        advanceTimeBy(1)
        runCurrent()

        assertTrue(setup.await())
        assertEquals(1, discoveries)
    }

    @Test
    fun repeatedSetupAndLateCallbacksDoNotRepeatGattOperations() = runTest {
        var mtuRequests = 0
        var discoveries = 0
        val coordinator = PcBleGattSetupCoordinator(mtuTimeoutMs = 1)
        val requestMtu: (Int) -> Boolean = {
            mtuRequests += 1
            true
        }
        val discoverServices: () -> Boolean = {
            discoveries += 1
            true
        }

        val firstSetup = async { coordinator.startServiceDiscovery(requestMtu, discoverServices) }
        advanceTimeBy(1)
        runCurrent()
        assertTrue(firstSetup.await())

        coordinator.onMtuChanged(true)
        coordinator.onMtuChanged(false)
        val secondSetup = coordinator.startServiceDiscovery(requestMtu, discoverServices)

        assertTrue(secondSetup)
        assertEquals(1, mtuRequests)
        assertEquals(1, discoveries)
    }

    @Test
    fun serviceDiscoveryRejectionIsReturned() = runTest {
        val coordinator = PcBleGattSetupCoordinator()

        val started = coordinator.startServiceDiscovery(
            requestMtu = { false },
            discoverServices = { false }
        )

        assertFalse(started)
    }
}
