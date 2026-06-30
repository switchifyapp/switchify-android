package com.enaboapps.switchify.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashUploadWorkerTest {
    @After
    fun tearDown() {
        Logger.resetForTesting()
        CrashReporter.resetForTesting()
    }

    @Test
    fun successfulUploadDeletesCrashFile() {
        val directory = tempDirectory()
        val file = CrashReporter.writeCrashRecord(directory, crashRecord())!!
        Logger.setTelemetryEnabledOverrideForTesting { true }
        Logger.setUserIdOverrideForTesting { "device:test" }
        Logger.setSenderForTesting(TimberlogsSender { 204 })

        val outcome = CrashReporter.uploadQueuedCrashFiles(listOf(file))

        assertEquals(CrashUploadOutcome.Success, outcome)
        assertFalse(file.exists())
    }

    @Test
    fun nonSuccessUploadKeepsCrashFileAndRetries() {
        val directory = tempDirectory()
        val file = CrashReporter.writeCrashRecord(directory, crashRecord())!!
        Logger.setTelemetryEnabledOverrideForTesting { true }
        Logger.setUserIdOverrideForTesting { "device:test" }
        Logger.setSenderForTesting(TimberlogsSender { 500 })

        val outcome = CrashReporter.uploadQueuedCrashFiles(listOf(file))

        assertEquals(CrashUploadOutcome.Retry, outcome)
        assertTrue(file.exists())
    }

    @Test
    fun networkExceptionKeepsCrashFileAndRetries() {
        val directory = tempDirectory()
        val file = CrashReporter.writeCrashRecord(directory, crashRecord())!!
        Logger.setTelemetryEnabledOverrideForTesting { true }
        Logger.setUserIdOverrideForTesting { "device:test" }
        Logger.setSenderForTesting(TimberlogsSender { throw IllegalStateException("offline") })

        val outcome = CrashReporter.uploadQueuedCrashFiles(listOf(file))

        assertEquals(CrashUploadOutcome.Retry, outcome)
        assertTrue(file.exists())
    }

    @Test
    fun malformedCrashFileIsDeletedAndWorkerContinues() {
        val directory = tempDirectory()
        val malformed = kotlin.io.path.createTempFile(directory.toPath(), "crash_bad", ".json").toFile()
        malformed.writeText("{")
        val valid = CrashReporter.writeCrashRecord(directory, crashRecord(id = "valid"))!!
        Logger.setTelemetryEnabledOverrideForTesting { true }
        Logger.setUserIdOverrideForTesting { "device:test" }
        Logger.setSenderForTesting(TimberlogsSender { 204 })

        val outcome = CrashReporter.uploadQueuedCrashFiles(listOf(malformed, valid))

        assertEquals(CrashUploadOutcome.Success, outcome)
        assertFalse(malformed.exists())
        assertFalse(valid.exists())
    }

    @Test
    fun firstTransientFailureLeavesRemainingFiles() {
        val directory = tempDirectory()
        val first = CrashReporter.writeCrashRecord(directory, crashRecord(id = "first", timestamp = 1))!!
        val second = CrashReporter.writeCrashRecord(directory, crashRecord(id = "second", timestamp = 2))!!
        Logger.setTelemetryEnabledOverrideForTesting { true }
        Logger.setUserIdOverrideForTesting { "device:test" }
        Logger.setSenderForTesting(TimberlogsSender { 500 })

        val outcome = CrashReporter.uploadQueuedCrashFiles(listOf(first, second))

        assertEquals(CrashUploadOutcome.Retry, outcome)
        assertTrue(first.exists())
        assertTrue(second.exists())
    }

    @Test
    fun telemetryDisabledPurgesQueuedCrashFiles() {
        val directory = tempDirectory()
        val file = CrashReporter.writeCrashRecord(directory, crashRecord())!!

        CrashReporter.purgeCrashFiles(listOf(file))

        assertFalse(file.exists())
    }

    @Test
    fun uploadWorkRequestRequiresConnectedNetwork() {
        val request = CrashReporter.createUploadWorkRequest()

        assertEquals(androidx.work.NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    private fun crashRecord(id: String = "id", timestamp: Long = 1L): CrashRecord {
        return CrashRecord(
            id = id,
            exceptionClass = "java.lang.RuntimeException",
            message = "boom",
            stackTrace = "stack",
            threadName = "main",
            timestamp = timestamp,
            versionName = "test",
            versionCode = 1,
            sdkInt = 35,
            manufacturer = "Google",
            model = "Pixel",
            processName = "com.enaboapps.switchify",
            breadcrumbs = emptyList()
        )
    }

    private fun tempDirectory(): File {
        return Files.createTempDirectory("switchify-crash-upload-test").toFile()
    }
}
