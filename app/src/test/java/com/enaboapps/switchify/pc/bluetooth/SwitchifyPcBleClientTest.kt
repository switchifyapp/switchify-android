package com.enaboapps.switchify.pc.bluetooth

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcControlCloseReason
import com.enaboapps.switchify.pc.PcControlConnectionEvent
import com.enaboapps.switchify.pc.PcDeviceIdentity
import com.enaboapps.switchify.pc.PcErrorReason
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcKeyboardShortcutKey
import com.enaboapps.switchify.pc.PcLiveControlResult
import com.enaboapps.switchify.pc.PcPairingResult
import com.enaboapps.switchify.pc.PcPairingTokenStore
import com.enaboapps.switchify.pc.PcPingResult
import com.enaboapps.switchify.pc.PcStoredPairing
import com.enaboapps.switchify.pc.PcTransport
import com.enaboapps.switchify.pc.PcWindowControlAction
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchifyPcBleClientTest {
    private val endpoint = PcBluetoothEndpoint(
        deviceAddress = "AA:BB:CC:DD:EE:FF",
        deviceName = "Switchify PC",
        desktopId = "desktop-1",
        displayName = "Switchify PC"
    )
    private val pc = DiscoveredPc(
        serviceName = "Switchify PC",
        desktopId = "desktop-1",
        bluetoothEndpoint = endpoint
    )

    @Test
    fun pairingSendsExistingProtocolWithoutSavingToken() = runTest {
        val tokens = FakeTokenStore()
        val transport = FakeTransportFactory(
            responseProvider = { message ->
                val json = JSONObject(message)
                assertEquals("pairing.request", json.getString("type"))
                assertEquals("desktop-1", json.getJSONObject("payload").getString("desktopId"))
                pairingComplete(json.getString("id"))
            }
        )
        val client = client(tokens, transport)

        val result = client.requestApproval(pc, "nonce-1")

        assertTrue(result is PcPairingResult.Paired)
        assertNull(tokens.getToken("desktop-1"))
        assertNull(tokens.getLastEndpointId("desktop-1"))
    }

    @Test
    fun pairingMismatchDoesNotSaveToken() = runTest {
        val tokens = FakeTokenStore()
        val transport = FakeTransportFactory { message ->
            pairingComplete(JSONObject(message).getString("id"), desktopId = "other-desktop")
        }
        val client = client(tokens, transport)

        val result = client.requestApproval(pc, "nonce-1")

        assertTrue(result is PcPairingResult.Failed)
        assertNull(tokens.getToken("desktop-1"))
    }

    @Test
    fun pairingRejectedMapsMessage() = runTest {
        val transport = FakeTransportFactory { message ->
            error(JSONObject(message).getString("id"), "invalid_auth", "pairing_rejected")
        }
        val result = client(FakeTokenStore(), transport).requestApproval(pc, "nonce-1")

        assertEquals(PcPairingResult.Failed(PcErrorReason.PairingRejected, "Request rejected."), result)
    }

    @Test
    fun authenticatedPingSendsExistingProtocol() = runTest {
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            assertEquals("connection.ping", json.getString("type"))
            ack(json.getString("id"))
        }
        val result = client(FakeTokenStore(), transport).authenticatedPing(pc, "token")

        assertEquals(PcPingResult.Connected("AA:BB:CC:DD:EE:FF"), result)
    }

    @Test
    fun invalidAuthMapsToAuthFailed() = runTest {
        val transport = FakeTransportFactory { message ->
            error(JSONObject(message).getString("id"), "invalid_auth", "invalid_auth")
        }
        val result = client(FakeTokenStore(), transport).authenticatedPing(pc, "token")

        assertTrue(result is PcPingResult.AuthFailed)
    }

    @Test
    fun openControlSessionConnectsSubscribesAndPings() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        val seenTypes = mutableListOf<String>()
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            seenTypes += json.getString("type")
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> pointerProfile(json.getString("id"))
                else -> ack(json.getString("id"))
            }
        }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)

        val result = client(tokens, transport).openControlSession(session)

        assertTrue(result is com.enaboapps.switchify.pc.PcLiveControlResult.Connected)
        assertEquals(listOf("connection.ping", "pointer.profile"), seenTypes)
    }

    @Test
    fun openControlSessionMapsBluetoothOffToSafeFailure() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        val transport = ThrowingTransportFactory(IllegalStateException("Bluetooth is off."))
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)

        val result = client(tokens, transport).openControlSession(session)

        assertEquals(com.enaboapps.switchify.pc.PcLiveControlResult.Failed("Bluetooth is off."), result)
    }

    @Test
    fun openControlSessionMapsPermissionDeniedToSafeFailure() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        val transport = ThrowingTransportFactory(IllegalStateException("Bluetooth permission missing."))
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)

        val result = client(tokens, transport).openControlSession(session)

        assertEquals(com.enaboapps.switchify.pc.PcLiveControlResult.Failed("Bluetooth permission denied."), result)
    }

    @Test
    fun openControlSessionMapsPointerProfileInvalidAuthToAuthFailedAndClosesConnection() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        lateinit var fakeConnection: FakeConnection
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> error(json.getString("id"), "invalid_auth", "invalid_auth")
                else -> ack(json.getString("id"))
            }
        }.also { factory -> factory.onConnection = { fakeConnection = it } }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)

        val result = client(tokens, transport).openControlSession(session)

        assertEquals(PcLiveControlResult.AuthFailed(), result)
        assertEquals(listOf(PcControlCloseReason.AuthFailure.logName), fakeConnection.closeReasons)
    }

    @Test
    fun liveConnectionForwardsDisconnectEvents() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        lateinit var fakeConnection: FakeConnection
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> pointerProfile(json.getString("id"))
                else -> ack(json.getString("id"))
            }
        }.also { factory -> factory.onConnection = { fakeConnection = it } }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)
        val result = client(tokens, transport).openControlSession(session) as com.enaboapps.switchify.pc.PcLiveControlResult.Connected

        val event = async { result.connection.connectionEvents.first() }
        fakeConnection.eventsFlow.emit(PcBleTransportEvent.Disconnected)

        assertEquals(PcControlConnectionEvent.Disconnected, event.await())
    }

    @Test
    fun liveConnectionHealthCheckSendsAuthenticatedPing() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        val seenTypes = mutableListOf<String>()
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            seenTypes += json.getString("type")
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> pointerProfile(json.getString("id"))
                else -> ack(json.getString("id"))
            }
        }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)
        val result = client(tokens, transport).openControlSession(session) as com.enaboapps.switchify.pc.PcLiveControlResult.Connected

        assertEquals(PcCommandResult.Ack, result.connection.checkHealth())

        assertEquals(listOf("connection.ping", "pointer.profile", "connection.ping"), seenTypes)
    }

    @Test
    fun commandsReuseExistingProtocolBuilders() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        val seenTypes = mutableListOf<String>()
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            seenTypes += json.getString("type")
            ack(json.getString("id"))
        }
        val client: PcConnector = client(tokens, transport)
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)

        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.LeftClick))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.DoubleClick))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.RightClick))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.DragStart()))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.DragEnd()))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.Move(1, 2)))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.Scroll(0, 10)))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.TypeText("hello")))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.PressKey(PcKeyboardKey.Enter)))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Meta))))
        assertEquals(PcCommandResult.Ack, client.sendCommand(session, PcControlCommand.WindowControl(PcWindowControlAction.MinimizeFocused)))

        assertEquals(
            listOf(
                "mouse.click",
                "mouse.doubleClick",
                "mouse.rightClick",
                "mouse.dragStart",
                "mouse.dragEnd",
                "mouse.move",
                "mouse.scroll",
                "keyboard.typeText",
                "keyboard.key",
                "keyboard.shortcut",
                "window.control"
            ),
            seenTypes
        )
    }

    @Test
    fun realtimeMoveFallsBackWhenPointerProfileDoesNotAdvertiseNoAck() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        lateinit var fakeConnection: FakeConnection
        val seenTypes = mutableListOf<String>()
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            seenTypes += json.getString("type")
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> pointerProfile(json.getString("id"))
                else -> ack(json.getString("id"))
            }
        }.also { factory -> factory.onConnection = { fakeConnection = it } }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)
        val result = client(tokens, transport).openControlSession(session) as PcLiveControlResult.Connected

        assertEquals(PcCommandResult.Ack, result.connection.sendRealtimeCommand(PcControlCommand.Move(4, 5)))

        assertEquals(listOf("connection.ping", "pointer.profile", "mouse.move"), seenTypes)
        assertTrue(fakeConnection.sentMessages.isEmpty())
    }

    @Test
    fun realtimeMoveUsesSendOnlyWhenPointerProfileAdvertisesNoAck() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        lateinit var fakeConnection: FakeConnection
        val seenTypes = mutableListOf<String>()
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            seenTypes += json.getString("type")
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> pointerProfile(json.getString("id"), noAckMouseMove = true)
                else -> ack(json.getString("id"))
            }
        }.also { factory -> factory.onConnection = { fakeConnection = it } }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)
        val result = client(tokens, transport).openControlSession(session) as PcLiveControlResult.Connected

        assertEquals(PcCommandResult.Ack, result.connection.sendRealtimeCommand(PcControlCommand.Move(4, 5)))

        assertEquals(listOf("connection.ping", "pointer.profile"), seenTypes)
        val message = JSONObject(fakeConnection.sentMessages.single())
        assertEquals("mouse.move", message.getString("type"))
        assertEquals("none", message.getString("responseMode"))
        assertEquals(4, message.getJSONObject("payload").getInt("dx"))
        assertEquals(5, message.getJSONObject("payload").getInt("dy"))
    }

    @Test
    fun realtimeMoveWriteFailureReturnsFailed() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        lateinit var fakeConnection: FakeConnection
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> pointerProfile(json.getString("id"), noAckMouseMove = true)
                else -> ack(json.getString("id"))
            }
        }.also { factory -> factory.onConnection = { fakeConnection = it } }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)
        val result = client(tokens, transport).openControlSession(session) as PcLiveControlResult.Connected
        fakeConnection.sendError = IllegalStateException("Bluetooth write timed out.")

        assertEquals(PcCommandResult.Failed(), result.connection.sendRealtimeCommand(PcControlCommand.Move(4, 5)))
    }

    @Test
    fun realtimeNonMoveCommandUsesReliablePath() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        lateinit var fakeConnection: FakeConnection
        val seenTypes = mutableListOf<String>()
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            seenTypes += json.getString("type")
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> pointerProfile(json.getString("id"), noAckMouseMove = true)
                else -> ack(json.getString("id"))
            }
        }.also { factory -> factory.onConnection = { fakeConnection = it } }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)
        val result = client(tokens, transport).openControlSession(session) as PcLiveControlResult.Connected

        assertEquals(PcCommandResult.Ack, result.connection.sendRealtimeCommand(PcControlCommand.LeftClick))

        assertEquals(listOf("connection.ping", "pointer.profile", "mouse.click"), seenTypes)
        assertTrue(fakeConnection.sentMessages.isEmpty())
    }

    @Test
    fun realtimeCommandUsesSendOnlyWhenPointerProfileAdvertisesCommand() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        lateinit var fakeConnection: FakeConnection
        val seenTypes = mutableListOf<String>()
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            seenTypes += json.getString("type")
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> pointerProfile(
                    json.getString("id"),
                    noAckCommands = listOf("mouse.click", "keyboard.typeText", "keyboard.shortcut", "window.control")
                )
                else -> ack(json.getString("id"))
            }
        }.also { factory -> factory.onConnection = { fakeConnection = it } }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)
        val result = client(tokens, transport).openControlSession(session) as PcLiveControlResult.Connected

        assertEquals(PcCommandResult.Ack, result.connection.sendRealtimeCommand(PcControlCommand.LeftClick))
        assertEquals(PcCommandResult.Ack, result.connection.sendRealtimeCommand(PcControlCommand.TypeText("Hello")))
        assertEquals(PcCommandResult.Ack, result.connection.sendRealtimeCommand(PcControlCommand.KeyboardShortcut(listOf(PcKeyboardShortcutKey.Meta))))
        assertEquals(PcCommandResult.Ack, result.connection.sendRealtimeCommand(PcControlCommand.WindowControl(PcWindowControlAction.SwitchNext)))

        assertEquals(listOf("connection.ping", "pointer.profile"), seenTypes)
        val messages = fakeConnection.sentMessages.map(::JSONObject)
        assertEquals(listOf("mouse.click", "keyboard.typeText", "keyboard.shortcut", "window.control"), messages.map { it.getString("type") })
        assertTrue(messages.all { it.getString("responseMode") == "none" })
        assertEquals("Meta", messages[2].getJSONObject("payload").getJSONArray("keys").getString(0))
    }

    @Test
    fun realtimeCommandFallsBackWhenPointerProfileDoesNotAdvertiseCommand() = runTest {
        val tokens = FakeTokenStore(mutableMapOf("desktop-1" to "token"), mutableMapOf("desktop-1" to "Switchify PC"))
        lateinit var fakeConnection: FakeConnection
        val seenTypes = mutableListOf<String>()
        val transport = FakeTransportFactory { message ->
            val json = JSONObject(message)
            seenTypes += json.getString("type")
            when (json.getString("type")) {
                "connection.ping" -> ack(json.getString("id"))
                "pointer.profile" -> pointerProfile(json.getString("id"), noAckCommands = listOf("mouse.click"))
                else -> ack(json.getString("id"))
            }
        }.also { factory -> factory.onConnection = { fakeConnection = it } }
        val session = PcAuthenticatedSession("desktop-1", "device-1", "AA:BB:CC:DD:EE:FF", PcTransport.Bluetooth)
        val result = client(tokens, transport).openControlSession(session) as PcLiveControlResult.Connected

        assertEquals(PcCommandResult.Ack, result.connection.sendRealtimeCommand(PcControlCommand.Scroll(0, 5)))

        assertEquals(listOf("connection.ping", "pointer.profile", "mouse.scroll"), seenTypes)
        assertTrue(fakeConnection.sentMessages.isEmpty())
    }

    private fun client(tokens: FakeTokenStore, transport: PcBleTransportFactory): SwitchifyPcBleClient {
        return SwitchifyPcBleClient(FakeIdentity, tokens, transport)
    }

    private fun ack(id: String): String = """{"version":1,"id":"$id","type":"ack","ok":true,"error":null}"""

    private fun pairingComplete(
        id: String,
        desktopId: String = "desktop-1",
        deviceId: String = "device-1"
    ): String {
        return """{"version":1,"id":"$id","type":"pairing.complete","ok":true,"error":null,"payload":{"desktopId":"$desktopId","deviceId":"$deviceId","token":"token"}}"""
    }

    private fun error(id: String, code: String, message: String): String {
        return """{"version":1,"id":"$id","type":"error","ok":false,"error":{"code":"$code","message":"$message"}}"""
    }

    private fun pointerProfile(
        id: String,
        noAckMouseMove: Boolean = false,
        noAckCommands: List<String> = emptyList()
    ): String {
        val capabilityItems = mutableListOf<String>()
        if (noAckMouseMove) capabilityItems += """"noAckMouseMove":true"""
        if (noAckCommands.isNotEmpty()) {
            capabilityItems += """"noAckCommands":[${noAckCommands.joinToString(",") { """"$it"""" }}]"""
        }
        val capabilities = if (capabilityItems.isNotEmpty()) ""","capabilities":{${capabilityItems.joinToString(",")}}""" else ""
        return """{"version":1,"id":"$id","type":"pointer.profile","ok":true,"error":null,"payload":{"displayId":"0:0:1280:720:1.0","scaleFactor":1.0,"bounds":{"x":0,"y":0,"width":1280,"height":720},"maxDelta":500,"recommendedDeltas":{"small":40,"medium":80,"large":160}$capabilities}}"""
    }

    private object FakeIdentity : PcDeviceIdentity {
        override fun getDeviceId(): String = "device-1"
        override fun getDeviceName(): String = "Android phone"
    }

    private class FakeTokenStore(
        private val tokens: MutableMap<String, String> = mutableMapOf(),
        private val serviceNames: MutableMap<String, String> = mutableMapOf()
    ) : PcPairingTokenStore {
        private val lastEndpointIds = mutableMapOf<String, String>()

        override fun getToken(desktopId: String): String? = tokens[desktopId]

        override fun saveToken(desktopId: String, token: String, lastEndpointId: String, serviceName: String?) {
            tokens[desktopId] = token
            lastEndpointIds[desktopId] = lastEndpointId
            if (!serviceName.isNullOrBlank()) serviceNames[desktopId] = serviceName
        }

        override fun clearToken(desktopId: String) {
            tokens.remove(desktopId)
            lastEndpointIds.remove(desktopId)
            serviceNames.remove(desktopId)
        }

        override fun listPairings(): List<PcStoredPairing> {
            return tokens.keys.map { PcStoredPairing(it, serviceNames[it], lastEndpointIds[it]) }
        }

        override fun getLastEndpointId(desktopId: String): String? = lastEndpointIds[desktopId]
        override fun getServiceName(desktopId: String): String? = serviceNames[desktopId]
    }

    private class FakeTransportFactory(
        private val responseProvider: (String) -> String
    ) : PcBleTransportFactory {
        var onConnection: ((FakeConnection) -> Unit)? = null

        override suspend fun connect(endpoint: PcBluetoothEndpoint): PcBleTransportConnection {
            return FakeConnection(endpoint, responseProvider).also { onConnection?.invoke(it) }
        }
    }

    private class ThrowingTransportFactory(private val error: Throwable) : PcBleTransportFactory {
        override suspend fun connect(endpoint: PcBluetoothEndpoint): PcBleTransportConnection {
            throw error
        }
    }

    private class FakeConnection(
        override val endpoint: PcBluetoothEndpoint,
        private val responseProvider: (String) -> String
    ) : PcBleTransportConnection {
        val eventsFlow = MutableSharedFlow<PcBleTransportEvent>(replay = 1, extraBufferCapacity = 8)
        override val events = eventsFlow
        val closeReasons = mutableListOf<String>()
        val sentMessages = mutableListOf<String>()
        val receivedMessages = mutableListOf<String>()
        var sendError: Throwable? = null

        override suspend fun send(message: String) {
            sendError?.let { throw it }
            sentMessages += message
        }

        override suspend fun sendAndReceive(message: String, timeoutMs: Long): String {
            receivedMessages += message
            return responseProvider(message)
        }

        override fun close(reason: String) {
            closeReasons += reason
        }
    }
}
