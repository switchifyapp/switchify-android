package com.enaboapps.switchify.pc.connection

import com.enaboapps.switchify.pc.protocol.PcCanonical
import com.enaboapps.switchify.pc.protocol.PcClock
import com.enaboapps.switchify.pc.protocol.PcCommands
import com.enaboapps.switchify.pc.protocol.PcFrameReassembler
import com.enaboapps.switchify.pc.protocol.PcFraming
import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.protocol.PcReassemblyResult
import com.enaboapps.switchify.pc.protocol.PcResponse
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import com.enaboapps.switchify.pc.storage.InMemoryPcKeyValueStore
import com.enaboapps.switchify.pc.storage.PcPairingStore
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.pc.transport.PcBluetoothAvailability
import com.enaboapps.switchify.pc.transport.PcBluetoothException
import com.enaboapps.switchify.pc.transport.PcDiscoveredDesktop
import com.enaboapps.switchify.pc.transport.PcTransport
import com.enaboapps.switchify.pc.transport.PcUnsubscribe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun savedOffice(lastConnectedAt: Long = 1) =
    PcSavedPc("desktop-1", "Office", PcPlatform.Windows, "ble-1", lastConnectedAt)

private val office = PcDiscoveredDesktop("desktop-1", "Office", PcPlatform.Windows, null, "ble-1", -45)

private class LoopbackTransport(private val scope: CoroutineScope) : PcTransport {
    var availability = PcBluetoothAvailability.Ready
    var resolved = office
    var connectFailures = 0
    var resolveCount = 0
    var disconnectCount = 0
    var healthChecks = 0
    var healthResult = true
    var token = "fixture-secret"
    var pairingResponse: ((JSONObject) -> String)? = null
    var pingErrorCode: String? = null
    val dropCounts = mutableMapOf<String, Int>()
    val dropTypes = mutableSetOf<String>()
    val failWriteTypes = mutableSetOf<String>()
    val responseGates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val messages = mutableListOf<JSONObject>()
    var onFrame: ((ByteArray) -> Unit)? = null
    var onNotificationError: ((Throwable) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var onScanDesktop: ((PcDiscoveredDesktop) -> Unit)? = null
    var onScanError: ((Throwable) -> Unit)? = null
    var scanStopped = false
    private val outbound = PcFrameReassembler()

    val types: List<String> get() = messages.map { it.getString("type") }

    override suspend fun availability() = availability

    override fun scan(onDesktop: (PcDiscoveredDesktop) -> Unit, onError: (Throwable) -> Unit): PcUnsubscribe {
        scanStopped = false
        onScanDesktop = onDesktop
        onScanError = onError
        return PcUnsubscribe { scanStopped = true }
    }

    override suspend fun connect(peripheralId: String) = Unit

    override suspend fun resolveAndConnect(desktopId: String): PcDiscoveredDesktop {
        resolveCount += 1
        if (connectFailures > 0) {
            connectFailures -= 1
            throw PcBluetoothException("fixture connect failed")
        }
        return resolved
    }

    override suspend fun disconnect() {
        disconnectCount += 1
    }

    override fun maxWriteValueBytes() = 182

    override suspend fun cancelPendingWrites() = Unit

    override suspend fun verifyConnection(desktopId: String): Boolean {
        healthChecks += 1
        return healthResult
    }

    override fun subscribe(onFrame: (ByteArray) -> Unit, onError: (Throwable) -> Unit): PcUnsubscribe {
        this.onFrame = onFrame
        onNotificationError = onError
        return PcUnsubscribe {
            this.onFrame = null
            onNotificationError = null
        }
    }

    override suspend fun notificationsReady() = Unit

    override fun subscribeDisconnect(onDisconnect: () -> Unit): PcUnsubscribe {
        this.onDisconnect = onDisconnect
        return PcUnsubscribe { this.onDisconnect = null }
    }

    override suspend fun writeFrame(frame: ByteArray) {
        val decoded = PcFraming.decodeFrame(frame) ?: throw PcBluetoothException("invalid fixture frame")
        val message = (outbound.accept(decoded) as? PcReassemblyResult.Complete)?.message ?: return
        val request = JSONObject(message)
        messages += request
        val type = request.getString("type")
        val id = request.getString("id")
        if (type in failWriteTypes) throw PcBluetoothException("fixture write failed")
        val drops = dropCounts[type] ?: 0
        if (drops > 0) {
            dropCounts[type] = drops - 1
            return
        }
        if (type in dropTypes) return
        val response = when {
            type == "pairing.request" -> pairingResponse?.invoke(request) ?: pairingComplete(request)
            type == "connection.ping" && pingErrorCode != null -> error(id, pingErrorCode!!, pingErrorCode!!)
            type == "pointer.profile" -> pointerProfile(id)
            else -> ack(id)
        }
        val gate = responseGates[type]
        scope.launch {
            gate?.await()
            emit(response, id)
        }
    }

    fun emit(response: String, id: String) {
        PcFraming.createFrames(response, "response-$id").forEach { onFrame?.invoke(PcFraming.encodeFrame(it)) }
    }

    fun pairingComplete(request: JSONObject): String {
        val payload = request.getJSONObject("payload")
        return JSONObject()
            .put("version", 1)
            .put("id", request.getString("id"))
            .put("type", "pairing.complete")
            .put("ok", true)
            .put("error", JSONObject.NULL)
            .put(
                "payload",
                JSONObject()
                    .put("desktopId", payload.getString("desktopId"))
                    .put("deviceId", payload.getString("deviceId"))
                    .put("token", token)
            )
            .toString()
    }

    companion object {
        fun ack(id: String) = """{"version":1,"id":"$id","type":"ack","ok":true,"error":null}"""

        fun error(id: String, code: String, message: String) =
            """{"version":1,"id":"$id","type":"error","ok":false,"error":{"code":"$code","message":"$message"}}"""

        fun pointerProfile(id: String) = """{"version":1,"id":"$id","type":"pointer.profile","ok":true,"error":null,
            "payload":{"displayId":"display-1","scaleFactor":1.5,"bounds":{"x":0,"y":0,"width":1280,"height":720},
            "maxDelta":256,"recommendedDeltas":{"small":32,"medium":128,"large":256},
            "capabilities":{"pointerSpeed":{"supported":true,"setSupported":true,"scalePercent":100}}}}"""
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PcConnectionManagerTest {
    private class Harness(test: TestScope) {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler))
        val transport = LoopbackTransport(scope)
        val publicStore = InMemoryPcKeyValueStore()
        val secretStore = InMemoryPcKeyValueStore()
        val storage = PcPairingStore(publicStore, secretStore, PcIdGenerator { "device-1" })
        val diagnostics = PcDiagnosticLog(PcClock { 0 })
        var permission = true
        var now = 1_000L
        private var requestCounter = 0
        private var nonceCounter = 0
        val reconnectDelays = mutableListOf<Long>()
        val manager = PcConnectionManager(
            transport = transport,
            storage = storage,
            diagnostics = diagnostics,
            requestPermission = { permission },
            clock = PcClock { now },
            requestIds = PcIdGenerator { "req-${++requestCounter}" },
            nonces = PcIdGenerator { "nonce-${++nonceCounter}" },
            reconnectDelay = { reconnectDelays += it },
            remoteName = { "Owen's Pixel" },
            scope = scope
        )

        val codes: List<String> get() = diagnostics.entries.value.map { it.code }.reversed()

        suspend fun savePc(pc: PcSavedPc = savedOffice(), token: String = "saved-secret") = storage.save(pc, token)
    }

    private fun managerTest(block: suspend TestScope.(Harness) -> Unit) = runTest {
        val harness = Harness(this)
        try {
            block(harness)
        } finally {
            harness.scope.cancel()
        }
    }

    private suspend fun TestScope.connect(h: Harness) {
        h.manager.connect(office)
        runCurrent()
        assertTrue(h.manager.state.value is PcConnectionState.Connected)
    }

    @Test
    fun pairsWithTheVerificationCodeThenStoresTheTokenAndAuthenticates() = managerTest { h ->
        val approval = CompletableDeferred<Unit>()
        h.transport.responseGates["pairing.request"] = approval
        h.manager.scan()
        runCurrent()
        h.transport.onScanDesktop?.invoke(office)
        runCurrent()
        assertEquals(listOf(office), (h.manager.state.value as PcConnectionState.Scanning).discovered)

        backgroundScope.launch { h.manager.connect(office) }
        runCurrent()
        val pairing = h.manager.state.value as PcConnectionState.Pairing
        assertEquals("215918", pairing.verificationCode)
        assertEquals("desktop-1", pairing.desktop.desktopId)
        assertTrue(h.transport.scanStopped)
        val request = h.transport.messages.single()
        assertEquals("pairing.request", request.getString("type"))
        assertFalse(request.has("auth"))
        val payload = request.getJSONObject("payload")
        assertEquals("device-1", payload.getString("deviceId"))
        assertEquals("Owen's Pixel", payload.getString("deviceName"))
        assertEquals("desktop-1", payload.getString("desktopId"))
        assertEquals("nonce-1", payload.getString("requestNonce"))

        approval.complete(Unit)
        runCurrent()
        val connected = h.manager.state.value as PcConnectionState.Connected
        assertEquals(PcProfileStatus.Ready, connected.profileStatus)
        assertEquals("display-1", connected.profile?.displayId)
        assertEquals(listOf("pairing.request", "connection.ping", "pointer.profile"), h.transport.types.take(3))
        val ping = h.transport.messages[1]
        assertEquals("Owen's Pixel", ping.getJSONObject("payload").getString("deviceName"))
        assertEquals(
            PcCanonical.authProof(
                ping.getString("id"), "device-1", ping.getLong("timestamp"), "connection.ping",
                linkedMapOf("deviceName" to "Owen's Pixel"), "fixture-secret"
            ),
            ping.getString("auth")
        )
        assertEquals("fixture-secret", h.storage.token("desktop-1"))
        assertEquals(listOf("desktop-1"), h.storage.list().map { it.desktopId })
        assertTrue(h.codes.containsAll(listOf("scan_started", "connecting", "pairing_requested", "connected")))
    }

    @Test
    fun rejectionOnThePcFailsWithoutSavingAccess() = managerTest { h ->
        h.transport.pairingResponse = { LoopbackTransport.error(it.getString("id"), "invalid_auth", "pairing_rejected") }
        h.manager.connect(office)
        runCurrent()
        assertEquals(PcConnectionFailure.PairingRejected, (h.manager.state.value as PcConnectionState.Failed).failure)
        assertNull(h.storage.token("desktop-1"))
        assertTrue("pairing_rejected" in h.codes)
        assertEquals(listOf("pairing.request"), h.transport.types)
    }

    @Test
    fun expiryReportedByThePcFailsAsExpired() = managerTest { h ->
        h.transport.pairingResponse = { LoopbackTransport.error(it.getString("id"), "invalid_auth", "pairing_request_expired") }
        h.manager.connect(office)
        runCurrent()
        assertEquals(PcConnectionFailure.PairingExpired, (h.manager.state.value as PcConnectionState.Failed).failure)
    }

    @Test
    fun unapprovedPairingExpiresAfterTwoMinutes() = managerTest { h ->
        h.transport.dropTypes += "pairing.request"
        backgroundScope.launch { h.manager.connect(office) }
        advanceTimeBy(PcConnectionManager.PAIRING_TIMEOUT_MS - 1)
        assertTrue(h.manager.state.value is PcConnectionState.Pairing)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(PcConnectionFailure.PairingExpired, (h.manager.state.value as PcConnectionState.Failed).failure)
        assertNull(h.storage.token("desktop-1"))
    }

    @Test
    fun mismatchedPairingCompletionIsNotAccepted() = managerTest { h ->
        h.transport.pairingResponse = { request ->
            h.transport.pairingComplete(request).replace("\"desktop-1\"", "\"desktop-2\"")
        }
        h.manager.connect(office)
        runCurrent()
        assertEquals(PcConnectionFailure.CouldNotConnect, (h.manager.state.value as PcConnectionState.Failed).failure)
        assertNull(h.storage.token("desktop-1"))
        assertNull(h.storage.token("desktop-2"))
    }

    @Test
    fun cancellingPairingIgnoresALateApproval() = managerTest { h ->
        val approval = CompletableDeferred<Unit>()
        h.transport.responseGates["pairing.request"] = approval
        backgroundScope.launch { h.manager.connect(office) }
        runCurrent()
        assertTrue(h.manager.state.value is PcConnectionState.Pairing)
        h.manager.disconnect()
        approval.complete(Unit)
        runCurrent()
        assertTrue(h.manager.state.value is PcConnectionState.Idle)
        assertNull(h.storage.token("desktop-1"))
    }

    @Test
    fun authenticatesASavedPcWithoutAnotherPairingRequest() = managerTest { h ->
        h.savePc()
        h.now = 5_000
        h.manager.connectSaved(savedOffice())
        runCurrent()
        assertTrue(h.manager.state.value is PcConnectionState.Connected)
        assertEquals(listOf("connection.ping", "pointer.profile"), h.transport.types)
        assertEquals(5_000L, h.storage.list().single().lastConnectedAt)
        assertEquals("saved-secret", h.storage.token("desktop-1"))
    }

    @Test
    fun invalidatesRejectedSavedAccessWithoutReopeningPairing() = managerTest { h ->
        h.savePc()
        h.transport.pingErrorCode = "invalid_auth"
        h.manager.connectSaved(savedOffice())
        runCurrent()
        val failed = h.manager.state.value as PcConnectionState.Failed
        assertEquals(PcConnectionFailure.SavedAccessInvalid, failed.failure)
        assertEquals(emptyList<PcSavedPc>(), failed.saved)
        assertNull(h.storage.token("desktop-1"))
        assertFalse("pairing.request" in h.transport.types)
        assertTrue("authentication_failed" in h.codes)

        h.transport.pingErrorCode = null
        h.manager.connect(office)
        runCurrent()
        assertTrue("pairing.request" in h.transport.types)
        assertTrue(h.manager.state.value is PcConnectionState.Connected)
    }

    @Test
    fun keepsAuthenticationWorkingWhenThePcCannotStoreTheName() = managerTest { h ->
        h.savePc()
        h.transport.pingErrorCode = "name_update_failed"
        h.manager.connectSaved(savedOffice())
        runCurrent()
        assertTrue(h.manager.state.value is PcConnectionState.Connected)
        assertTrue("remote_name_sync_failed" in h.codes)
    }

    @Test
    fun neverOpensPairingWhenSavedMetadataHasNoToken() = managerTest { h ->
        h.savePc()
        h.secretStore.values.remove(PcPairingStore.tokenKey("desktop-1"))
        h.manager.connectSaved(savedOffice())
        runCurrent()
        assertEquals(PcConnectionFailure.SavedAccessUnavailable, (h.manager.state.value as PcConnectionState.Failed).failure)
        assertEquals(0, h.transport.resolveCount)
        assertTrue(h.transport.messages.isEmpty())
    }

    @Test
    fun doesNotRemoveSavedMetadataWhenSecureTokenLoadingFails() = managerTest { h ->
        h.savePc()
        h.secretStore.failGet = { it == PcPairingStore.tokenKey("desktop-1") }
        h.manager.connectSaved(savedOffice())
        runCurrent()
        assertEquals(PcConnectionFailure.SavedAccessLoadFailed, (h.manager.state.value as PcConnectionState.Failed).failure)
        h.secretStore.failGet = { false }
        assertEquals("saved-secret", h.storage.token("desktop-1"))
    }

    @Test
    fun keepsPermissionAndBluetoothStateOutsideTheTransport() = managerTest { h ->
        h.permission = false
        h.manager.scan()
        assertTrue(h.manager.state.value is PcConnectionState.PermissionDenied)
        assertNull(h.transport.onScanDesktop)
        h.permission = true
        h.transport.availability = PcBluetoothAvailability.PoweredOff
        h.manager.scan()
        assertTrue(h.manager.state.value is PcConnectionState.BluetoothOff)
        h.transport.availability = PcBluetoothAvailability.Unsupported
        h.manager.scan()
        assertTrue(h.manager.state.value is PcConnectionState.Unsupported)
    }

    @Test
    fun reportsAScanFailureAsDiscoveryFailed() = managerTest { h ->
        h.manager.scan()
        h.transport.onScanError?.invoke(PcBluetoothException("fixture"))
        runCurrent()
        assertEquals(PcConnectionFailure.DiscoveryFailed, (h.manager.state.value as PcConnectionState.Failed).failure)
        assertTrue("scan_failed" in h.codes)
    }

    @Test
    fun ordersTheDefaultBeforeMoreRecentlyConnectedPcs() = managerTest { h ->
        h.savePc(PcSavedPc("pc-old", "Old", PcPlatform.MacOs, "ble-old", 1), "a")
        h.savePc(PcSavedPc("pc-new", "New", PcPlatform.Windows, "ble-new", 2), "b")
        h.manager.load()
        assertEquals(listOf("pc-new", "pc-old"), h.manager.state.value.savedPcs?.map { it.desktopId })
        h.manager.setDefaultDesktopId("pc-old")
        assertEquals(listOf("pc-old", "pc-new"), h.manager.state.value.savedPcs?.map { it.desktopId })
        assertEquals("pc-old", h.manager.defaultDesktopId())
        h.manager.setDefaultDesktopId(null)
        assertEquals(listOf("pc-new", "pc-old"), h.manager.state.value.savedPcs?.map { it.desktopId })
    }

    @Test
    fun connectsTheExplicitDefaultAndOtherwiseTheMostRecentSavedPc() = managerTest { h ->
        h.transport.resolved = office.copy(desktopId = "pc-new")
        h.savePc(PcSavedPc("pc-old", "Old", PcPlatform.Windows, "ble-old", 1), "a")
        h.savePc(PcSavedPc("pc-new", "New", PcPlatform.Windows, "ble-new", 2), "b")
        h.manager.connectPreferred()
        runCurrent()
        assertEquals("pc-new", (h.manager.state.value as PcConnectionState.Connected).desktop.desktopId)

        h.manager.disconnect()
        h.manager.setDefaultDesktopId("pc-old")
        h.transport.resolved = office.copy(desktopId = "pc-old")
        h.manager.connectPreferred()
        runCurrent()
        assertEquals("pc-old", (h.manager.state.value as PcConnectionState.Connected).desktop.desktopId)
    }

    @Test
    fun appDisconnectRunsCleanupNotifiesSessionEndAndDoesNotReconnect() = managerTest { h ->
        connect(h)
        runCurrent()
        assertTrue(h.manager.state.value is PcConnectionState.Connected)
        val events = mutableListOf<String>()
        h.manager.registerCleanup {
            events += "cleanup:${h.manager.state.value::class.simpleName}"
            events += "sent:${h.manager.send(PcCommands.repeatStop())}"
        }
        h.manager.addSessionEndListener { events += "ended:$it" }
        val staleDisconnect = h.transport.onDisconnect
        val resolves = h.transport.resolveCount

        h.manager.disconnect()
        staleDisconnect?.invoke()
        runCurrent()

        assertTrue(h.manager.state.value is PcConnectionState.Idle)
        assertEquals(listOf("cleanup:Connected", "sent:true", "ended:Disconnected"), events)
        assertEquals("mouse.repeat.stop", h.transport.types.last())
        assertEquals(resolves, h.transport.resolveCount)
        assertTrue(h.codes.containsAll(listOf("cleanup_complete", "disconnected")))
        assertFalse("connection_lost" in h.codes)
    }

    @Test
    fun remoteDisconnectReconnectsWithTheSavedToken() = managerTest { h ->
        connect(h)
        runCurrent()
        val ended = mutableListOf<PcSessionEndReason>()
        h.manager.addSessionEndListener { ended += it }
        h.transport.onDisconnect?.invoke()
        runCurrent()
        runCurrent()
        assertTrue(h.manager.state.value is PcConnectionState.Connected)
        assertEquals(listOf(PcSessionEndReason.ConnectionLost), ended)
        assertEquals(listOf(500L), h.reconnectDelays)
        assertEquals(2, h.transport.resolveCount)
        assertEquals(1, h.transport.types.count { it == "pairing.request" })
        assertTrue("connection_lost" in h.codes)
    }

    @Test
    fun failsAfterBoundedReconnectAttempts() = managerTest { h ->
        connect(h)
        runCurrent()
        h.transport.connectFailures = 3
        h.transport.onNotificationError?.invoke(PcBluetoothException("fixture"))
        runCurrent()
        assertEquals(PcConnectionFailure.ConnectionLost, (h.manager.state.value as PcConnectionState.Failed).failure)
        assertEquals(listOf(500L, 1_000L, 1_500L), h.reconnectDelays)
        assertEquals(4, h.transport.resolveCount)
    }

    @Test
    fun checksAnIdleConnectionAfterFiveSecondsAndReconnectsWhenTheCheckFails() = managerTest { h ->
        connect(h)
        runCurrent()
        assertTrue(h.manager.state.value is PcConnectionState.Connected)
        advanceTimeBy(PcConnectionManager.HEALTH_INTERVAL_MS + 1)
        assertEquals(1, h.transport.healthChecks)
        h.transport.healthResult = false
        h.transport.connectFailures = 3
        advanceTimeBy(PcConnectionManager.HEALTH_INTERVAL_MS + 1)
        runCurrent()
        assertEquals(2, h.transport.healthChecks)
        assertTrue("connection_health_failed" in h.codes)
        runCurrent()
        assertEquals(PcConnectionFailure.ConnectionLost, (h.manager.state.value as PcConnectionState.Failed).failure)
    }

    @Test
    fun sendsAuthenticatedCommandsAndReconnectsAfterAWriteFailure() = managerTest { h ->
        connect(h)
        runCurrent()
        assertTrue(h.manager.send(PcCommands.click()))
        assertEquals(PcResponse.Ack("req-5"), h.manager.request(PcCommands.click()))
        assertTrue(h.manager.send(PcCommands.move(1.0, 2.0), PcResponseMode.None))
        assertEquals("none", h.transport.messages.last().getString("responseMode"))

        h.transport.failWriteTypes += "mouse.click"
        h.transport.connectFailures = 3
        assertNull(h.manager.request(PcCommands.click()))
        runCurrent()
        assertEquals(listOf(500L, 1_000L, 1_500L), h.reconnectDelays)
        assertTrue("connection_lost" in h.codes)
        assertEquals(PcConnectionFailure.ConnectionLost, (h.manager.state.value as PcConnectionState.Failed).failure)
        assertTrue("command_failed" in h.codes)
    }

    @Test
    fun revokedAccessDuringACommandFailsTheConnection() = managerTest { h ->
        connect(h)
        runCurrent()
        h.transport.pingErrorCode = "invalid_auth"
        assertEquals(PcRemoteNameSync.Failed, h.manager.syncRemoteName())
        assertEquals(PcConnectionFailure.AccessRevoked, (h.manager.state.value as PcConnectionState.Failed).failure)
        assertEquals(PcRemoteNameSync.Deferred, h.manager.syncRemoteName())
    }

    @Test
    fun appliesAnAcknowledgedPointerSpeedToTheProfile() = managerTest { h ->
        connect(h)
        runCurrent()
        assertTrue(h.manager.send(PcCommands.pointerSpeed(150.0)))
        val profile = (h.manager.state.value as PcConnectionState.Connected).profile!!
        assertEquals(150.0, profile.capabilities.pointerSpeed.scalePercent, 0.0)
    }

    @Test
    fun retriesAMissingPointerProfileOnceWithAFreshRequestId() = managerTest { h ->
        h.transport.dropCounts["pointer.profile"] = 1
        backgroundScope.launch { h.manager.connect(office) }
        advanceTimeBy(PcConnectionManager.PROFILE_TIMEOUT_MS + 10)
        runCurrent()
        val connected = h.manager.state.value as PcConnectionState.Connected
        assertEquals(PcProfileStatus.Ready, connected.profileStatus)
        val profileIds = h.transport.messages.filter { it.getString("type") == "pointer.profile" }.map { it.getString("id") }
        assertEquals(2, profileIds.toSet().size)
    }

    @Test
    fun recoversThePointerProfileInTheBackgroundAfterBothInitialAttemptsFail() = managerTest { h ->
        h.transport.dropCounts["pointer.profile"] = 2
        backgroundScope.launch { h.manager.connect(office) }
        advanceTimeBy(2 * PcConnectionManager.PROFILE_TIMEOUT_MS + 10)
        runCurrent()
        assertEquals(PcProfileStatus.Recovering, (h.manager.state.value as PcConnectionState.Connected).profileStatus)
        advanceTimeBy(1_000 + 10)
        runCurrent()
        assertEquals(PcProfileStatus.Ready, (h.manager.state.value as PcConnectionState.Connected).profileStatus)
        assertTrue(h.codes.containsAll(listOf("profile_recovery_started", "profile_recovered")))
    }

    @Test
    fun exhaustsProfileRecoveryAfterFiveRequests() = managerTest { h ->
        h.transport.dropTypes += "pointer.profile"
        backgroundScope.launch { h.manager.connect(office) }
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(PcProfileStatus.Unavailable, (h.manager.state.value as PcConnectionState.Connected).profileStatus)
        assertEquals(5, h.transport.types.count { it == "pointer.profile" })
        assertTrue("profile_recovery_exhausted" in h.codes)
    }

    @Test
    fun unpairDisconnectsTheActivePcAndRemovesItsAccess() = managerTest { h ->
        connect(h)
        runCurrent()
        assertTrue(h.manager.unpair("desktop-1"))
        assertTrue(h.manager.state.value is PcConnectionState.Idle)
        assertEquals(emptyList<PcSavedPc>(), h.manager.state.value.savedPcs)
        assertNull(h.storage.token("desktop-1"))
    }

    @Test
    fun surfacesASanitizedUnpairFailureWithoutHidingTheSavedPc() = managerTest { h ->
        h.savePc()
        h.secretStore.failRemove = { true }
        assertFalse(h.manager.unpair("desktop-1"))
        val failed = h.manager.state.value as PcConnectionState.Failed
        assertEquals(PcConnectionFailure.UnpairFailed, failed.failure)
        assertEquals(listOf("desktop-1"), failed.saved.map { it.desktopId })
        assertTrue("unpair_failed" in h.codes)
    }

    @Test
    fun switchingToTheActivePcIsIgnored() = managerTest { h ->
        connect(h)
        runCurrent()
        val resolves = h.transport.resolveCount
        h.manager.switchSaved(savedOffice())
        runCurrent()
        assertEquals(resolves, h.transport.resolveCount)
        assertTrue(h.manager.state.value is PcConnectionState.Connected)
    }

    @Test
    fun neverRecordsSecretsInDiagnostics() = managerTest { h ->
        connect(h)
        runCurrent()
        h.manager.disconnect()
        val export = h.diagnostics.export()
        assertFalse(export.contains("fixture-secret"))
        assertFalse(export.contains("ble-1"))
    }
}
