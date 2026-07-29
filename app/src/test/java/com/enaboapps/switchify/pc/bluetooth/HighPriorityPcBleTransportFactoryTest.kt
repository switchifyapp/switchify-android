package com.enaboapps.switchify.pc.bluetooth

import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HighPriorityPcBleTransportFactoryTest {
    @Test
    fun requestsHighPriorityBeforeReturningConnectionAndRestoresItOnceOnClose() = runTest {
        val delegate = RecordingConnection(priorityAccepted = true)
        val factory = HighPriorityPcBleTransportFactory(FixedFactory(delegate))

        val connection = factory.connect(delegate.endpoint)

        assertEquals(listOf(true), delegate.priorityRequests)
        assertSame(delegate.endpoint, connection.endpoint)

        connection.close("first")
        connection.close("second")

        assertEquals(listOf(true, false), delegate.priorityRequests)
        assertEquals(listOf("first"), delegate.closeReasons)
    }

    @Test
    fun rejectedHighPriorityRequestDoesNotFailConnection() = runTest {
        val delegate = RecordingConnection(priorityAccepted = false)
        val factory = HighPriorityPcBleTransportFactory(FixedFactory(delegate))

        val connection = factory.connect(delegate.endpoint)
        connection.send("message", PcBleWriteMode.WithResponse)

        assertEquals(listOf(true), delegate.priorityRequests)
        assertEquals(listOf("message"), delegate.sentMessages)
    }

    private class FixedFactory(
        private val connection: PcBleTransportConnection
    ) : PcBleTransportFactory {
        override suspend fun connect(endpoint: PcBluetoothEndpoint): PcBleTransportConnection {
            return connection
        }
    }

    private class RecordingConnection(
        private val priorityAccepted: Boolean
    ) : PcBleTransportConnection {
        override val endpoint = PcBluetoothEndpoint(
            "AA:BB:CC:DD:EE:FF",
            null,
            "desktop-1",
            "Switchify PC"
        )
        override val events = emptyFlow<PcBleTransportEvent>()
        val priorityRequests = mutableListOf<Boolean>()
        val closeReasons = mutableListOf<String>()
        val sentMessages = mutableListOf<String>()

        override suspend fun send(message: String, writeMode: PcBleWriteMode) {
            sentMessages += message
        }

        override suspend fun sendAndReceive(
            message: String,
            requestId: String,
            timeoutMs: Long
        ): String = ""

        override fun requestHighPriority(enabled: Boolean): Boolean {
            priorityRequests += enabled
            return priorityAccepted
        }

        override fun close(reason: String) {
            closeReasons += reason
        }
    }
}
