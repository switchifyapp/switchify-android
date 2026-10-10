package com.enaboapps.switchify.pc.transport

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
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
    fun boundsAnOperationThatNeverCompletesAndClosesTheQueue() = runTest {
        var timedOut = 0
        val queue = PcGattOperationQueue(1_000, onTimedOut = { timedOut += 1 })
        val hung = async { runCatching { queue.execute<Unit>(write) { true } } }
        advanceTimeBy(1_001)
        assertEquals("Bluetooth operation timed out.", hung.await().exceptionOrNull()?.message)
        assertEquals(1, timedOut)
        assertFalse(queue.complete(write, Unit))
        var started = false
        val next = runCatching { queue.execute<ByteArray>(write) { started = true; true } }
        assertEquals("Bluetooth connection closed.", next.exceptionOrNull()?.message)
        assertFalse(started)
    }

    @Test
    fun keepsTheQueueHeldUntilACancelledOperationsCallbackArrives() = runTest {
        val queue = PcGattOperationQueue(1_000)
        val starts = mutableListOf<String>()
        val first = async { queue.execute<ByteArray>(read) { starts += "first"; true } }
        runCurrent()
        first.cancel()
        val second = async { queue.execute<ByteArray>(read) { starts += "second"; true } }
        runCurrent()
        assertEquals(listOf("first"), starts)
        assertTrue(queue.complete(read, byteArrayOf(1)))
        runCurrent()
        assertTrue(first.isCancelled)
        assertEquals(listOf("first", "second"), starts)
        assertTrue(queue.complete(read, byteArrayOf(2)))
        assertEquals(2.toByte(), second.await().single())
    }

    @Test
    fun releasesACancelledOperationOnlyWhenItTimesOut() = runTest {
        val queue = PcGattOperationQueue(1_000)
        val first = async { queue.execute<Unit>(write) { true } }
        runCurrent()
        first.cancel()
        var secondStarted = false
        val second = async { runCatching { queue.execute<Unit>(write) { secondStarted = true; true } } }
        advanceTimeBy(999)
        runCurrent()
        assertFalse(secondStarted)
        advanceTimeBy(2)
        runCurrent()
        assertFalse(secondStarted)
        assertTrue(second.await().isFailure)
    }

    @Test
    fun mapsMissingPermissionsToASanitizedError() = runTest {
        val queue = PcGattOperationQueue(1_000)
        val result = runCatching { queue.execute<Unit>(write) { throw SecurityException("native detail") } }
        assertEquals("Bluetooth permission is missing.", result.exceptionOrNull()?.message)
        assertTrue(result.exceptionOrNull() is PcBluetoothException)
    }

    @Test
    fun enablesNotificationsBeforeALaterDescriptorReadCanStart() = runTest {
        val queue = PcGattOperationQueue(1_000)
        val cccd = PcGattOperationKey(PcGattOperationType.WriteDescriptor, receive)
        val cccdRead = PcGattOperationKey(PcGattOperationType.ReadDescriptor, receive)
        var descriptor = byteArrayOf(0x00, 0x00)
        var pendingWrite: ByteArray? = null
        var readStarted = false
        val subscriber = PcNotificationSubscriber(
            scope = backgroundScope,
            setNotification = { true },
            writeConfiguration = { value -> queue.execute<Unit>(cccd) { pendingWrite = value; true } }
        )
        subscriber.enable {}
        val readBack = async(start = CoroutineStart.UNDISPATCHED) {
            queue.execute<ByteArray>(cccdRead) { readStarted = true; true }
        }
        runCurrent()
        assertFalse(readStarted)
        descriptor = pendingWrite!!
        queue.complete(cccd, Unit)
        runCurrent()
        assertTrue(readStarted)
        queue.complete(cccdRead, descriptor)
        assertArrayEquals(byteArrayOf(0x01, 0x00), readBack.await())

        subscriber.disable()
        val afterDisable = async(start = CoroutineStart.UNDISPATCHED) {
            queue.execute<ByteArray>(cccdRead) { true }
        }
        runCurrent()
        descriptor = pendingWrite!!
        queue.complete(cccd, Unit)
        runCurrent()
        queue.complete(cccdRead, descriptor)
        assertArrayEquals(byteArrayOf(0x00, 0x00), afterDisable.await())
    }

    @Test
    fun reportsSanitizedNotificationSetupFailures() = runTest {
        val errors = mutableListOf<Throwable>()
        PcNotificationSubscriber(backgroundScope, { throw SecurityException("native detail") }, {}).enable { errors += it }
        runCurrent()
        assertEquals("Bluetooth notifications could not be enabled.", errors.single().message)
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
