package com.enaboapps.switchify.pc.transport

import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.protocol.PcResponseTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcBleTransportTest {
    private val adapter = FakeGattAdapter()
    private val stages = mutableListOf<String>()

    private fun TestScope.transport(observer: PcConnectionStageObserver? = PcConnectionStageObserver { stage, outcome ->
        stages += "${stage.code}_${outcome.code}"
    }) = PcBleTransport(adapter, StandardTestDispatcher(testScheduler), nativeTimeoutMs = 1_000, stageObserver = observer)

    @Test
    fun connectsWithHighPriorityAndTheLargeMtuBeforeServiceDiscovery() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        val transport = transport()
        transport.connect("AA")
        assertEquals(listOf("connect:AA", "priority:AA", "mtu:AA:517", "services:AA", "status:AA"), adapter.log)
        assertEquals(514, transport.maxWriteValueBytes())
        assertEquals(
            listOf(
                "ble_connect_started", "ble_connect_succeeded", "ble_priority_started", "ble_priority_succeeded",
                "ble_mtu_started", "ble_mtu_succeeded", "ble_services_started", "ble_services_succeeded"
            ),
            stages
        )
    }

    @Test
    fun fallsBackToTheConservativeWriteSizeWhenTheMtuIsUnknown() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        adapter.negotiatedMtu = null
        val transport = transport()
        transport.connect("AA")
        assertEquals(182, transport.maxWriteValueBytes())
    }

    @Test
    fun rejectsUnknownResponseTransportsWithoutNotificationFallback() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1", extra = ",\"responseTransport\":\"future\"")
        val transport = transport()
        val result = runCatching { transport.connect("AA") }
        assertEquals("Bluetooth discovery status is invalid.", result.exceptionOrNull()?.message)
        assertTrue(adapter.latest("AA").disconnected)
        assertTrue(runCatching { transport.maxWriteValueBytes() }.isFailure)
    }

    @Test
    fun usesSerialResponseReadsForNegotiatedReadRepliesAndNeverNotifications() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("linux", "linux", ",\"responseTransport\":\"read-v1\"")
        val transport = transport()
        transport.connect("AA")
        val connection = adapter.latest("AA")
        connection.responseReads += "frame".toByteArray()
        val frames = mutableListOf<String>()
        val unsubscribe = transport.subscribe({ frames += it.toString(Charsets.UTF_8) }, {})
        transport.notificationsReady()
        runCurrent()
        assertEquals(listOf("frame"), frames)
        assertEquals(0, connection.monitors)
        assertTrue(adapter.log.none { it.startsWith("cccd") })
        assertTrue(runCatching { transport.subscribe({}, {}) }.isFailure)
        unsubscribe.unsubscribe()
        runCurrent()
        val reads = connection.responseReadCount
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(reads, connection.responseReadCount)
        assertEquals(
            "Bluetooth writes are unavailable until reconnect.",
            runCatching { transport.writeFrame(byteArrayOf(1)) }.exceptionOrNull()?.message
        )
        transport.disconnect()
    }

    @Test
    fun poisonsWritesWhenAResponseReadFails() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("linux", "linux", ",\"responseTransport\":\"read-v1\"")
        val transport = transport()
        transport.connect("AA")
        adapter.latest("AA").responseReads += ByteArray(200)
        val errors = mutableListOf<Throwable>()
        transport.subscribe({}, { errors += it })
        assertTrue(runCatching { transport.notificationsReady() }.isFailure)
        assertEquals(1, errors.size)
        assertTrue(runCatching { transport.writeFrame(byteArrayOf(1)) }.isFailure)
        transport.disconnect()
    }

    @Test
    fun subscribesToTransmitNotificationsAndVerifiesTheDescriptor() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        val transport = transport()
        transport.connect("AA")
        val connection = adapter.latest("AA")
        val frames = mutableListOf<String>()
        transport.subscribe({ frames += it.toString(Charsets.UTF_8) }, {})
        transport.notificationsReady()
        connection.notificationListener?.invoke("frame".toByteArray())
        assertEquals(listOf("frame"), frames)
        assertEquals(1, connection.monitors)
        assertTrue(stages.containsAll(listOf("ble_notifications_succeeded", "ble_notification_ready_succeeded")))
    }

    @Test
    fun rejectsWhenNotificationsAreNotEnabled() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        val transport = transport()
        transport.connect("AA")
        adapter.latest("AA").cccd = byteArrayOf(0x00, 0x00)
        transport.subscribe({}, {})
        assertEquals(
            "Bluetooth notifications could not be enabled.",
            runCatching { transport.notificationsReady() }.exceptionOrNull()?.message
        )
        assertTrue("ble_notification_ready_failed" in stages)
    }

    @Test
    fun boundsAHungWriteAndRequiresReconnectAfterwards() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        val transport = transport()
        transport.connect("AA")
        adapter.latest("AA").writeGate = CompletableDeferred()
        val hung = async { runCatching { transport.writeFrame(byteArrayOf(1)) } }
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals("Bluetooth write timed out.", hung.await().exceptionOrNull()?.message)
        assertEquals(
            "Bluetooth writes are unavailable until reconnect.",
            runCatching { transport.writeFrame(byteArrayOf(2)) }.exceptionOrNull()?.message
        )
        transport.connect("AA")
        transport.writeFrame(byteArrayOf(3))
        assertEquals(3.toByte(), adapter.latest("AA").writes.single().single())
    }

    @Test
    fun cancellingPendingWritesRejectsTheInFlightWrite() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        val transport = transport()
        transport.connect("AA")
        adapter.latest("AA").writeGate = CompletableDeferred()
        val pending = async { runCatching { transport.writeFrame(byteArrayOf(1)) } }
        runCurrent()
        transport.cancelPendingWrites()
        assertEquals("Bluetooth write was cancelled.", pending.await().exceptionOrNull()?.message)
    }

    @Test
    fun mapsNativeWriteFailuresToASanitizedError() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        val transport = transport()
        transport.connect("AA")
        adapter.latest("AA").writeError = IllegalStateException("native detail")
        assertEquals("Bluetooth write failed.", runCatching { transport.writeFrame(byteArrayOf(1)) }.exceptionOrNull()?.message)
    }

    @Test
    fun deduplicatesAdvertisementsLimitsProbesAndCancelsThemWhenScanningStops() = runTest {
        val transport = transport()
        val desktops = mutableListOf<PcDiscoveredDesktop>()
        (1..6).forEach { index ->
            adapter.statuses["P$index"] = FakeGattAdapter.status("pc-$index")
            adapter.connectGates["P$index"] = CompletableDeferred()
        }
        val stop = transport.scan({ desktops += it }, {})
        runCurrent()
        (1..6).forEach { adapter.advertise("P$it") }
        adapter.advertise("P1")
        runCurrent()
        assertEquals(4, adapter.maxActiveConnects)
        assertEquals(4, adapter.log.count { it.startsWith("connect:") })
        adapter.connectGates.getValue("P1").complete(Unit)
        runCurrent()
        assertEquals(listOf("pc-1"), desktops.map { it.desktopId })
        assertTrue(adapter.latest("P1").disconnected)
        assertEquals(5, adapter.log.count { it.startsWith("connect:") })
        adapter.advertise("P1")
        runCurrent()
        assertEquals(1, adapter.log.count { it == "connect:P1" })
        stop.unsubscribe()
        runCurrent()
        assertFalse(adapter.scanning)
        assertEquals(0, adapter.activeConnects)
    }

    @Test
    fun publishesTheBluetoothNameForWindowsAndTheStatusNameForMac() = runTest {
        val transport = transport()
        adapter.statuses["W"] = FakeGattAdapter.status("windows-pc")
        adapter.statuses["M"] =
            "{\"protocolVersion\":1,\"desktopId\":\"mac\",\"displayName\":\"Studio\",\"platform\":\"macos\"}".toByteArray()
        val desktops = mutableListOf<PcDiscoveredDesktop>()
        val stop = transport.scan({ desktops += it }, {})
        runCurrent()
        adapter.advertise("W", name = "OFFICE-PC")
        adapter.advertise("M", name = "Mac Bluetooth")
        runCurrent()
        assertEquals(mapOf("windows-pc" to "OFFICE-PC", "mac" to "Studio"), desktops.associate { it.desktopId to it.displayName })
        assertEquals(PcPlatform.MacOs, desktops.first { it.desktopId == "mac" }.platform)
        stop.unsubscribe()
        runCurrent()
    }

    @Test
    fun handsAMatchingDiscoveryConnectionDirectlyToTheSession() = runTest {
        adapter.statuses["OTHER"] = FakeGattAdapter.status("pc-other")
        adapter.statuses["MINE"] = FakeGattAdapter.status("pc-mine", extra = ",\"responseTransport\":\"read-v1\"")
        val transport = transport()
        val result = async { transport.resolveAndConnect("pc-mine") }
        runCurrent()
        adapter.advertise("OTHER")
        adapter.advertise("MINE")
        runCurrent()
        val desktop = result.await()
        assertEquals("pc-mine", desktop.desktopId)
        assertEquals(PcResponseTransport.ReadV1, desktop.responseTransport)
        assertEquals(1, adapter.connections.getValue("MINE").size)
        val mine = adapter.latest("MINE")
        assertFalse(mine.disconnected)
        assertTrue(adapter.latest("OTHER").disconnected)
        assertTrue("mtu:MINE:517" in adapter.log)
        assertFalse(adapter.scanning)
        assertEquals(514, transport.maxWriteValueBytes())
        assertTrue(stages.containsAll(listOf("ble_selected_match_not_matched", "ble_selected_match_succeeded", "ble_resolution_succeeded")))
        transport.disconnect()
        assertTrue(mine.disconnected)
    }

    @Test
    fun timesOutASavedPcResolutionAndIgnoresLateAdvertisements() = runTest {
        val transport = transport()
        val result = async { runCatching { transport.resolveAndConnect("pc-missing") } }
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals("Saved PC discovery timed out.", result.await().exceptionOrNull()?.message)
        assertFalse(adapter.scanning)
        assertTrue("ble_resolution_timed_out" in stages)
        adapter.advertise("LATE")
        runCurrent()
        assertTrue(adapter.log.none { it == "connect:LATE" })
    }

    @Test
    fun disconnectsARetainedProbeWhenSessionSetupFails() = runTest {
        adapter.statuses["MINE"] = FakeGattAdapter.status("pc-mine")
        adapter.mtuGate = CompletableDeferred()
        val transport = transport()
        val result = async { runCatching { transport.resolveAndConnect("pc-mine") } }
        runCurrent()
        adapter.advertise("MINE")
        runCurrent()
        val retained = adapter.latest("MINE")
        assertTrue("mtu:MINE:517" in adapter.log)
        assertFalse(retained.disconnected)
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(result.await().isFailure)
        assertTrue(retained.disconnected)
        assertTrue(runCatching { transport.maxWriteValueBytes() }.isFailure)
    }

    @Test
    fun verifiesTheCurrentPcWithoutReconnecting() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        val transport = transport()
        transport.connect("AA")
        assertTrue(transport.verifyConnection("pc-1"))
        assertFalse(transport.verifyConnection("pc-2"))
        adapter.latest("AA").connected = false
        assertFalse(transport.verifyConnection("pc-1"))
        assertEquals(1, adapter.log.count { it.startsWith("connect:") })
    }

    @Test
    fun reportsAvailabilityFromTheAdapter() = runTest {
        val transport = transport()
        mapOf(
            PcGattAdapterState.PoweredOn to PcBluetoothAvailability.Ready,
            PcGattAdapterState.PoweredOff to PcBluetoothAvailability.PoweredOff,
            PcGattAdapterState.Unauthorized to PcBluetoothAvailability.Unauthorized,
            PcGattAdapterState.Unsupported to PcBluetoothAvailability.Unsupported
        ).forEach { (state, availability) ->
            adapter.state = state
            assertEquals(availability, transport.availability())
        }
    }

    @Test
    fun doesNotLetStageObserverFailuresAffectConnection() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        val transport = transport(PcConnectionStageObserver { _, _ -> throw IllegalStateException("observer") })
        transport.connect("AA")
        assertEquals(514, transport.maxWriteValueBytes())
    }

    @Test
    fun forwardsUnexpectedDisconnects() = runTest {
        adapter.statuses["AA"] = FakeGattAdapter.status("pc-1")
        val transport = transport()
        transport.connect("AA")
        var disconnects = 0
        val unsubscribe = transport.subscribeDisconnect { disconnects += 1 }
        adapter.latest("AA").disconnectListener?.invoke()
        unsubscribe.unsubscribe()
        assertEquals(1, disconnects)
        assertEquals(null, adapter.latest("AA").disconnectListener)
    }
}
