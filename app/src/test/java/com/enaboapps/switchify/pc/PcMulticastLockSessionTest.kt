package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Test

class PcMulticastLockSessionTest {
    private class FakeLock : PcMulticastLock {
        var acquireCalls = 0
        var releaseCalls = 0
        var throwOnAcquire = false
        var throwOnRelease = false

        override fun acquire() {
            acquireCalls++
            if (throwOnAcquire) error("acquire failed")
        }

        override fun release() {
            releaseCalls++
            if (throwOnRelease) error("release failed")
        }
    }

    @Test
    fun acquireThenReleaseDelegatesOnce() {
        val lock = FakeLock()
        val session = PcMulticastLockSession(lock)

        session.acquire()
        session.release()

        assertEquals(1, lock.acquireCalls)
        assertEquals(1, lock.releaseCalls)
    }

    @Test
    fun repeatedAcquireIsIdempotent() {
        val lock = FakeLock()
        val session = PcMulticastLockSession(lock)

        session.acquire()
        session.acquire()

        assertEquals(1, lock.acquireCalls)
    }

    @Test
    fun releaseWithoutAcquireDoesNothing() {
        val lock = FakeLock()
        val session = PcMulticastLockSession(lock)

        session.release()

        assertEquals(0, lock.releaseCalls)
    }

    @Test
    fun repeatedReleaseDelegatesOnce() {
        val lock = FakeLock()
        val session = PcMulticastLockSession(lock)

        session.acquire()
        session.release()
        session.release()

        assertEquals(1, lock.releaseCalls)
    }

    @Test
    fun restartCycleBalancesAcquireAndRelease() {
        val lock = FakeLock()
        val session = PcMulticastLockSession(lock)

        // Mirrors startDiscovery() (which stops first) -> stopDiscovery()
        session.release()
        session.acquire()
        session.release()
        session.acquire()
        session.release()

        assertEquals(2, lock.acquireCalls)
        assertEquals(2, lock.releaseCalls)
    }

    @Test
    fun lockFailuresDoNotPreventStateTracking() {
        val lock = FakeLock().apply {
            throwOnAcquire = true
            throwOnRelease = true
        }
        val session = PcMulticastLockSession(lock)

        session.acquire()
        session.release()
        session.acquire()
        session.release()

        assertEquals(2, lock.acquireCalls)
        assertEquals(2, lock.releaseCalls)
    }
}
