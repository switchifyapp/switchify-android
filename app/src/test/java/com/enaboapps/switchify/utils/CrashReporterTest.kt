package com.enaboapps.switchify.utils

import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashReporterTest {
    private val gson = Gson()

    @After
    fun tearDown() {
        CrashReporter.resetForTesting()
        Logger.resetForTesting()
    }

    @Test
    fun multipleCrashRecordsCreateMultipleQueuedFiles() {
        val directory = tempDirectory()

        CrashReporter.writeCrashRecord(directory, crashRecord(id = "one", timestamp = 1))
        CrashReporter.writeCrashRecord(directory, crashRecord(id = "two", timestamp = 2))

        val files = CrashReporter.queuedCrashFiles(directory)
        assertEquals(2, files.size)
        assertTrue(files[0].name.contains("one"))
        assertTrue(files[1].name.contains("two"))
    }

    @Test
    fun queueRetentionPrunesOldestRecords() {
        val directory = tempDirectory()

        repeat(21) { index ->
            CrashReporter.writeCrashRecord(
                directory,
                crashRecord(id = "crash-$index", timestamp = index.toLong())
            )
        }

        val files = CrashReporter.queuedCrashFiles(directory)
        assertEquals(20, files.size)
        assertFalse(files.any { it.name.contains("crash-0") })
    }

    @Test
    fun legacyPendingCrashMigratesIntoQueue() {
        val directory = tempDirectory()
        val legacy = File(directory.parentFile, "pending_crash.json")
        legacy.writeText(
            """
            {
              "exceptionClass": "java.lang.IllegalStateException",
              "message": "legacy",
              "stackTrace": "stack",
              "threadName": "main",
              "timestamp": 12
            }
            """.trimIndent()
        )

        CrashReporter.migrateLegacyCrashIfPresent(directory, legacy)

        val files = CrashReporter.queuedCrashFiles(directory)
        assertEquals(1, files.size)
        assertFalse(legacy.exists())
        assertTrue(files.single().readText().contains("legacy"))
    }

    @Test
    fun corruptLegacyPendingCrashDoesNotCrashMigration() {
        val directory = tempDirectory()
        val legacy = File(directory.parentFile, "pending_crash.json")
        legacy.writeText("{")

        CrashReporter.migrateLegacyCrashIfPresent(directory, legacy)

        assertTrue(CrashReporter.queuedCrashFiles(directory).isEmpty())
        assertFalse(legacy.exists())
    }

    @Test
    fun uploadSchedulingIsDeferredWhileUserIsLocked() {
        var scheduleCount = 0

        val scheduled = CrashReporter.scheduleUploadIfUnlocked(false) {
            scheduleCount++
        }

        assertFalse(scheduled)
        assertEquals(0, scheduleCount)
    }

    @Test
    fun uploadSchedulingRunsAfterUserUnlocks() {
        var scheduleCount = 0

        val scheduled = CrashReporter.scheduleUploadIfUnlocked(true) {
            scheduleCount++
        }

        assertTrue(scheduled)
        assertEquals(1, scheduleCount)
    }

    @Test
    fun uploadSchedulingFailureDoesNotEscape() {
        val scheduled = CrashReporter.scheduleUploadIfUnlocked(true) {
            throw IllegalStateException("WorkManager unavailable")
        }

        assertFalse(scheduled)
    }

    @Test
    fun breadcrumbsAreIncludedAndCapped() {
        val directory = tempDirectory()
        Logger.setTelemetryEnabledOverrideForTesting { true }

        repeat(31) {
            CrashReporter.recordBreadcrumb(LogEvent.AppLaunched)
        }
        val file = CrashReporter.writeCrashRecord(
            directory,
            crashRecord(breadcrumbs = List(31) {
                CrashBreadcrumb(
                    eventName = "event-$it",
                    dataset = "app",
                    level = "info",
                    timestamp = it.toLong()
                )
            }.takeLast(30))
        )

        assertNotNull(file)
        val record = gson.fromJson(file!!.readText(), CrashRecord::class.java)
        assertEquals(30, record.breadcrumbs.size)
        assertEquals("event-1", record.breadcrumbs.first().eventName)
        assertEquals("event-30", record.breadcrumbs.last().eventName)
    }

    private fun crashRecord(
        id: String = "id",
        timestamp: Long = 1L,
        breadcrumbs: List<CrashBreadcrumb> = emptyList()
    ): CrashRecord {
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
            breadcrumbs = breadcrumbs
        )
    }

    private fun tempDirectory(): File {
        return Files.createTempDirectory("switchify-crash-test").toFile()
    }
}
