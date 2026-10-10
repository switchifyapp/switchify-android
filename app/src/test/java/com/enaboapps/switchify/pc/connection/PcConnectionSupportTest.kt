package com.enaboapps.switchify.pc.connection

import com.enaboapps.switchify.pc.protocol.PcClock
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.pc.transport.PcConnectionStage
import com.enaboapps.switchify.pc.transport.PcConnectionStageOutcome
import com.enaboapps.switchify.pc.transport.PcDiscoveredDesktop
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcConnectionSupportTest {
    @Test
    fun recordsStageDiagnosticsWithTheRemoteVocabularyInABoundedNewestFirstLog() {
        val log = PcDiagnosticLog(PcClock { 1_760_000_000_123 })
        repeat(205) { log.addConnectionStage(PcConnectionStage.Connect, PcConnectionStageOutcome.Started) }
        assertEquals(PcDiagnosticLog.MAX_ENTRIES, log.entries.value.size)
        assertTrue(log.export().contains("ble_connect_started: Connect to the selected PC: started."))
        log.onConnectionStage(PcConnectionStage.NotificationReady, PcConnectionStageOutcome.Failed)
        val newest = log.entries.value.first()
        assertEquals("ble_notification_ready_failed", newest.code)
        assertEquals(PcDiagnosticLevel.Warning, newest.level)
        log.addConnectionStage(PcConnectionStage.Resolution, PcConnectionStageOutcome.TimedOut)
        assertEquals("ble_resolution_timed_out", log.entries.value.first().code)
        assertEquals(PcDiagnosticLevel.Warning, log.entries.value.first().level)
        log.addConnectionStage(PcConnectionStage.SelectedMatch, PcConnectionStageOutcome.NotMatched)
        assertEquals(PcDiagnosticLevel.Info, log.entries.value.first().level)
        assertTrue(
            log.export().startsWith(
                "2025-10-09T08:53:20.123Z [info] ble_selected_match_not_matched: " +
                    "Match discovery status to the selected PC: not_matched."
            )
        )
        log.clear()
        assertEquals("", log.export())
    }

    @Test
    fun reportsUnexpectedErrorsWithoutTheirMessages() {
        val original = IllegalStateException("token fixture-secret", IllegalArgumentException("Office ble-1"))
        val sanitized = PcSanitizedError.from(original)
        assertEquals("java.lang.IllegalStateException", sanitized.message)
        assertEquals("java.lang.IllegalArgumentException", sanitized.cause?.message)
        assertTrue(original.stackTrace.contentEquals(sanitized.stackTrace))
        assertFalse(sanitized.stackTraceToString().contains("fixture-secret"))
        assertFalse(sanitized.stackTraceToString().contains("ble-1"))

        val first = IllegalStateException("a")
        val second = IllegalArgumentException("b", first)
        first.initCause(second)
        val cyclic = generateSequence<Throwable>(PcSanitizedError.from(first)) { it.cause }.count()
        assertTrue(cyclic in 2..9)
    }

    @Test
    fun describesEveryTransportStage() {
        PcConnectionStage.entries.forEach { stage ->
            assertTrue(PcDiagnosticLog.stageDescription(stage).isNotBlank())
        }
        val log = PcDiagnosticLog(PcClock { 0 })
        log.add(PcDiagnosticEvent.AuthenticationFailed, PcDiagnosticLevel.Error)
        assertEquals("authentication_failed", log.entries.value.single().code)
        assertEquals("Saved access is no longer valid.", log.entries.value.single().message)
    }

    @Test
    fun resolvesTheRemoteNameWithThePcNormalizationRules() {
        assertEquals("Pixel 9", PcRemoteName.resolve(null, "  Pixel 9 "))
        assertEquals("Kitchen tablet", PcRemoteName.resolve("Kitchen tablet", "Pixel 9"))
        assertEquals("Pixel 9", PcRemoteName.resolve("bad\nname", "Pixel 9"))
        assertEquals(PcRemoteName.FALLBACK, PcRemoteName.resolve(null, null))
        assertEquals(PcRemoteName.FALLBACK, PcRemoteName.resolve(null, "   "))
        assertEquals("a".repeat(40), PcRemoteName.validate("a".repeat(40)))
        assertNull(PcRemoteName.validate("a".repeat(41)))
        assertEquals("😀".repeat(40), PcRemoteName.validate("😀".repeat(40)))
    }

    @Test
    fun mergesSavedAndDiscoveredPcsByDesktopIdOnly() {
        val saved = PcSavedPc("pc-1", "Old name", PcPlatform.Windows, "old-address", 1)
        val discovered = PcDiscoveredDesktop("pc-1", "New name", PcPlatform.Windows, null, "new-address", -40)
        val stranger = PcDiscoveredDesktop("pc-2", "Old name", PcPlatform.MacOs, null, "old-address", -50)
        val rows = PcList.merge(listOf(saved), listOf(discovered, stranger))
        assertEquals(2, rows.size)
        assertEquals("New name", rows[0].desktop.displayName)
        assertEquals(saved, rows[0].saved)
        assertTrue(rows[0].nearby)
        assertEquals(PcListAction.Connect, rows[0].action)
        assertEquals("new-address", rows[0].savedForConnection()?.peripheralId)
        assertEquals(PcListAction.RequestAccess, rows[1].action)
        assertNull(rows[1].saved)
        val offline = PcList.merge(listOf(saved), emptyList()).single()
        assertFalse(offline.nearby)
        assertEquals("old-address", offline.desktop.peripheralId)
    }

        @Test
    fun requestsPermissionOnlyThroughAnAttachedScreen() = runTest {
        var granted = false
        val requester = PcPermissionRequester { granted }
        assertFalse(requester.request())

        requester.attachHost()
        val denied = async { requester.request() }
        runCurrent()
        assertTrue(requester.requested.value)
        requester.complete(false)
        assertFalse(denied.await())
        assertFalse(requester.requested.value)

        val allowed = async { requester.request() }
        runCurrent()
        granted = true
        requester.complete(true)
        assertTrue(allowed.await())
        assertTrue(requester.request())

        granted = false
        val abandoned = async { requester.request() }
        runCurrent()
        requester.detachHost()
        assertFalse(abandoned.await())
    }
}
