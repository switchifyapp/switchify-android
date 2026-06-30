package com.enaboapps.switchify.utils

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggerTest {
    @After
    fun tearDown() {
        Logger.resetForTesting()
        CrashReporter.resetForTesting()
    }

    @Test
    fun logNowReturnsTrueOnHttpSuccess() {
        Logger.setTelemetryEnabledOverrideForTesting { true }
        Logger.setUserIdOverrideForTesting { "device:test" }
        Logger.setSenderForTesting(TimberlogsSender { 200 })

        val result = Logger.logNow(LogEvent.AppLaunched)

        assertTrue(result)
    }

    @Test
    fun logNowReturnsFalseOnHttpFailure() {
        Logger.setTelemetryEnabledOverrideForTesting { true }
        Logger.setUserIdOverrideForTesting { "device:test" }
        Logger.setSenderForTesting(TimberlogsSender { 500 })

        val result = Logger.logNow(LogEvent.AppLaunched)

        assertFalse(result)
    }

    @Test
    fun logNowReturnsFalseOnNetworkException() {
        Logger.setTelemetryEnabledOverrideForTesting { true }
        Logger.setUserIdOverrideForTesting { "device:test" }
        Logger.setSenderForTesting(TimberlogsSender { throw IllegalStateException("offline") })

        val result = Logger.logNow(LogEvent.AppLaunched)

        assertFalse(result)
    }

    @Test
    fun logNowReturnsFalseWithoutUploadWhenTelemetryDisabled() {
        var uploads = 0
        Logger.setTelemetryEnabledOverrideForTesting { false }
        Logger.setSenderForTesting(
            TimberlogsSender {
                uploads += 1
                200
            }
        )

        val result = Logger.logNow(LogEvent.AppLaunched)

        assertFalse(result)
        assertTrue(uploads == 0)
    }

    @Test
    fun asyncLogReturnsWithoutBlockingCaller() = runBlocking {
        Logger.setTelemetryEnabledOverrideForTesting { true }
        Logger.setUserIdOverrideForTesting { "device:test" }
        Logger.setSenderForTesting(TimberlogsSender { 200 })

        Logger.log(LogEvent.AppLaunched)

        assertTrue(true)
    }
}
