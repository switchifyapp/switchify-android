package com.enaboapps.switchify.pc.bluetooth

import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcDeviceIdentity
import com.enaboapps.switchify.pc.PcProtocolResponse
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcBleProtocolMessengerTest {
    @Test
    fun authenticatedPingUsesInjectedIdentityRequestIdAndClock() = runTest {
        val connection = RecordingConnection()
        val messenger = PcBleProtocolMessenger(
            identityRepository = FakeIdentity,
            requestIdProvider = { "android-request-1" },
            now = { 123_456L }
        )

        val response = messenger.authenticatedPing(connection, "shared-token")

        assertTrue(response is PcProtocolResponse.Ack)
        val request = JSONObject(connection.lastMessage)
        assertEquals("android-request-1", request.getString("id"))
        assertEquals("device-1", request.getString("deviceId"))
        assertEquals(123_456L, request.getLong("timestamp"))
        assertEquals("connection.ping", request.getString("type"))
        assertEquals(PcBleProtocolMessenger.PING_TIMEOUT_MS, connection.lastTimeoutMs)
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class RecordingConnection : PcBleTransportConnection {
        override val endpoint = PcBluetoothEndpoint(
            "AA:BB:CC:DD:EE:FF",
            null,
            "desktop-1",
            "Switchify PC"
        )
        override val events = emptyFlow<PcBleTransportEvent>()
        lateinit var lastMessage: String
        var lastTimeoutMs: Long = 0

        override suspend fun send(message: String, writeMode: PcBleWriteMode) = Unit

        override suspend fun sendAndReceive(
            message: String,
            requestId: String,
            timeoutMs: Long
        ): String {
            lastMessage = message
            lastTimeoutMs = timeoutMs
            return """{"version":1,"id":"$requestId","type":"ack","ok":true,"error":null}"""
        }

        override fun requestHighPriority(enabled: Boolean): Boolean = true
        override fun close(reason: String) = Unit
    }
}
