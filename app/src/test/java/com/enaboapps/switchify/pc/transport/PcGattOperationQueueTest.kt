package com.enaboapps.switchify.pc.transport

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PcGattOperationQueueTest {
    private val receive = UUID.randomUUID()
    private val status = UUID.randomUUID()
    private val write = PcGattOperationKey(PcGattOperationType.WriteCharacteristic, receive)
    private val read = PcGattOperationKey(PcGattOperationType.ReadCharacteristic, status)

    @Test
    fun startsOnlyOneGattOperationAtATime() = runTest {
        val queue = PcGattOperationQueue(1_000)
        val started = mutableListOf<String>()
        val first = async { queue.execute<Unit>(write) { started += "write"; true } }
        val second = async { queue.execute<ByteArray>(read) { started += "read"; true } }
        runCurrent()
        assertEquals(listOf("write"), started)
        assertFalse(queue.complete(read, byteArrayOf(1)))
        assertTrue(queue.complete(write, Unit))
        first.await()
        runCurrent()
        assertEquals(listOf("write", "read"), started)
        assertTrue(queue.complete(read, byteArrayOf(7)))
        assertEquals(7.toByte(), second.await().single())
    }

    @Test
    fun boundsAnOperationThatNeverCompletesAndContinuesWithTheNext() = runTest {
        val queue = PcGattOperationQueue(1_000)
        val hung = async { runCatching { queue.execute<Unit>(write) { true } } }
        advanceTimeBy(1_001)
        assertEquals("Bluetooth operation timed out.", hung.await().exceptionOrNull()?.message)
        assertFalse(queue.complete(write, Unit))
        val next = async { queue.execute<ByteArray>(read) { true } }
        runCurrent()
        queue.complete(read, byteArrayOf())
        assertEquals(0, next.await().size)
    }

    @Test
    fun reportsRejectedStartsAndFailedCallbacks() = runTest {
        val queue = PcGattOperationQueue(1_000)
        val rejected = runCatching { queue.execute<Unit>(write) { false } }
        assertEquals("Bluetooth operation could not start.", rejected.exceptionOrNull()?.message)
        val failing = async { runCatching { queue.execute<Unit>(write) { true } } }
        runCurrent()
        queue.fail(write, PcBluetoothException("Bluetooth write failed."))
        assertEquals("Bluetooth write failed.", failing.await().exceptionOrNull()?.message)
    }

    @Test
    fun closingFailsThePendingOperationAndRejectsLaterOnes() = runTest {
        val queue = PcGattOperationQueue(1_000)
        val pending = async { runCatching { queue.execute<Unit>(write) { true } } }
        runCurrent()
        queue.close()
        assertTrue(pending.await().exceptionOrNull() is PcBluetoothException)
        var started = false
        val later = runCatching { queue.execute<Unit>(read) { started = true; true } }
        assertTrue(later.exceptionOrNull() is PcBluetoothException)
        assertFalse(started)
    }
}
