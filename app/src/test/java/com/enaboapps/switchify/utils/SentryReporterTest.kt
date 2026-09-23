package com.enaboapps.switchify.utils

import io.sentry.SentryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryReporterTest {
    @Test
    fun mapsLogEventLevelsToSentryLevels() {
        assertEquals(SentryLevel.DEBUG, SentryReporter.sentryLevel("debug"))
        assertEquals(SentryLevel.INFO, SentryReporter.sentryLevel("info"))
        assertEquals(SentryLevel.WARNING, SentryReporter.sentryLevel("warn"))
        assertEquals(SentryLevel.WARNING, SentryReporter.sentryLevel("warning"))
        assertEquals(SentryLevel.ERROR, SentryReporter.sentryLevel("error"))
        assertEquals(SentryLevel.FATAL, SentryReporter.sentryLevel("fatal"))
    }

    @Test
    fun defaultsUnknownLevelToInfo() {
        assertEquals(SentryLevel.INFO, SentryReporter.sentryLevel("verbose"))
    }

    @Test
    fun capturesErrorAndAboveOnly() {
        assertFalse(SentryReporter.isCapturable("debug"))
        assertFalse(SentryReporter.isCapturable("info"))
        assertFalse(SentryReporter.isCapturable("warn"))
        assertTrue(SentryReporter.isCapturable("error"))
        assertTrue(SentryReporter.isCapturable("fatal"))
    }

    @Test
    fun infoEventsBecomeBreadcrumbs() {
        assertFalse(SentryReporter.isCapturable(LogEvent.AppLaunched.level))
        assertFalse(SentryReporter.isCapturable(LogEvent.MenuOpened.level))
    }

    @Test
    fun warnLevelProductEventsStayBreadcrumbs() {
        assertFalse(SentryReporter.isCapturable(LogEvent.TrialExpired.level))
        assertFalse(SentryReporter.isCapturable(LogEvent.TrialWarningShown.level))
        assertFalse(SentryReporter.isCapturable(LogEvent.ScanTargetMissing.level))
    }

    @Test
    fun errorEventsAreCaptured() {
        assertTrue(SentryReporter.isCapturable(LogEvent.GoogleSignInSupabaseError.level))
        assertTrue(SentryReporter.isCapturable(LogEvent.ScanCycleFailed.level))
        assertTrue(SentryReporter.isCapturable(LogEvent.CameraBindFailed.level))
    }

    @Test
    fun truncatesOversizedExtras() {
        val truncated = SentryReporter.stringifyExtra("x".repeat(20_000))

        assertEquals(8 * 1024, truncated.length)
    }

    @Test
    fun stringifiesNonStringExtras() {
        assertEquals("42", SentryReporter.stringifyExtra(42))
        assertEquals("true", SentryReporter.stringifyExtra(true))
    }
}
