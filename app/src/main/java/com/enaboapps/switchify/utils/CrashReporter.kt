package com.enaboapps.switchify.utils

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.google.gson.Gson
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class CrashRecord(
    val id: String,
    val exceptionClass: String,
    val message: String?,
    val stackTrace: String,
    val threadName: String,
    val timestamp: Long,
    val versionName: String,
    val versionCode: Long,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val processName: String?,
    val breadcrumbs: List<CrashBreadcrumb>
)

internal data class CrashBreadcrumb(
    val eventName: String,
    val dataset: String,
    val level: String,
    val timestamp: Long
)

internal data class LegacyCrashRecord(
    val exceptionClass: String = "",
    val message: String? = null,
    val stackTrace: String = "",
    val threadName: String = "",
    val timestamp: Long = 0L
)

internal enum class CrashUploadOutcome {
    Success,
    Retry
}

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val LEGACY_CRASH_FILE = "pending_crash.json"
    private const val CRASH_DIR = "crashes"
    private const val MAX_QUEUED_CRASHES = 20
    private const val MAX_BREADCRUMBS = 30
    private const val MAX_STACK_TRACE_CHARS = 128 * 1024
    private const val UNIQUE_UPLOAD_WORK_NAME = "switchify_crash_upload"

    private val gson = Gson()
    private val lock = Any()
    private val breadcrumbs = ArrayDeque<CrashBreadcrumb>()
    private var installed = false
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        synchronized(lock) {
            if (installed) return
            val appContext = context.applicationContext
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                captureUnhandledCrash(appContext, thread, throwable)
                previousHandler?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    fun enqueueUpload(context: Context): Boolean {
        return scheduleUploadIfUnlocked(DeviceLockObserver.isUserUnlocked(context)) {
            migrateLegacyCrashIfPresent(context)
            val request = createUploadWorkRequest()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_UPLOAD_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    internal fun scheduleUploadIfUnlocked(
        isUserUnlocked: Boolean,
        schedule: () -> Unit
    ): Boolean {
        if (!isUserUnlocked) return false
        return try {
            schedule()
            true
        } catch (e: Exception) {
            safeLogE("Failed to schedule crash upload", e)
            false
        }
    }

    internal fun createUploadWorkRequest(): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<CrashUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
    }

    internal fun captureUnhandledCrash(context: Context, thread: Thread, throwable: Throwable): File? {
        if (!isTelemetryEnabled(context)) return null
        val record = CrashRecord(
            id = UUID.randomUUID().toString(),
            exceptionClass = throwable.javaClass.name,
            message = throwable.message,
            stackTrace = throwable.stackTraceToString().take(MAX_STACK_TRACE_CHARS),
            threadName = thread.name,
            timestamp = System.currentTimeMillis(),
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            sdkInt = sdkInt(),
            manufacturer = manufacturer(),
            model = model(),
            processName = currentProcessName(),
            breadcrumbs = snapshotBreadcrumbs()
        )
        return writeCrashRecord(crashDirectory(context), record)
    }

    internal fun recordBreadcrumb(event: LogEvent) {
        if (!Logger.isTelemetryEnabled()) return
        synchronized(lock) {
            breadcrumbs.addLast(
                CrashBreadcrumb(
                    eventName = event.eventName,
                    dataset = event.dataset,
                    level = event.level,
                    timestamp = System.currentTimeMillis()
                )
            )
            while (breadcrumbs.size > MAX_BREADCRUMBS) {
                breadcrumbs.removeFirst()
            }
        }
    }

    internal fun uploadQueuedCrashes(context: Context): CrashUploadOutcome {
        if (!isTelemetryEnabled(context)) {
            purgeQueuedCrashes(context)
            return CrashUploadOutcome.Success
        }

        migrateLegacyCrashIfPresent(context)

        return uploadQueuedCrashFiles(queuedCrashFiles(context))
    }

    internal fun uploadQueuedCrashFiles(files: List<File>): CrashUploadOutcome {
        files.forEach { file ->
            val record = try {
                gson.fromJson(file.readText(), CrashRecord::class.java)
            } catch (e: Exception) {
                safeLogE("Deleting malformed crash record ${file.name}", e)
                file.delete()
                return@forEach
            }

            val uploaded = Logger.logNow(
                event = LogEvent.UnhandledCrash,
                data = mapOf(
                    "crashId" to record.id,
                    "exceptionClass" to record.exceptionClass,
                    "message" to (record.message ?: ""),
                    "threadName" to record.threadName,
                    "stackTrace" to record.stackTrace,
                    "crashTimestamp" to record.timestamp,
                    "versionName" to record.versionName,
                    "versionCode" to record.versionCode,
                    "sdkInt" to record.sdkInt,
                    "manufacturer" to record.manufacturer,
                    "model" to record.model,
                    "processName" to (record.processName ?: ""),
                    "breadcrumbs" to record.breadcrumbs.map {
                        mapOf(
                            "eventName" to it.eventName,
                            "dataset" to it.dataset,
                            "level" to it.level,
                            "timestamp" to it.timestamp
                        )
                    }
                )
            )

            if (!uploaded) {
                return CrashUploadOutcome.Retry
            }
            file.delete()
        }
        return CrashUploadOutcome.Success
    }

    internal fun purgeCrashFiles(files: List<File>) {
        files.forEach { it.delete() }
    }

    internal fun queuedCrashFiles(context: Context): List<File> {
        return queuedCrashFiles(crashDirectory(context))
    }

    internal fun queuedCrashFiles(directory: File): List<File> {
        return directory.listFiles { file ->
            file.isFile && file.name.startsWith("crash_") && file.name.endsWith(".json")
        }?.sortedBy { it.name }.orEmpty()
    }

    internal fun writeCrashRecord(directory: File, record: CrashRecord): File? {
        if (!directory.exists() && !directory.mkdirs()) return null
        val finalFile = File(directory, "crash_${record.timestamp}_${record.id}.json")
        val tempFile = File(directory, "${finalFile.name}.tmp")
        return try {
            FileOutputStream(tempFile).use { output ->
                output.write(gson.toJson(record).toByteArray())
                output.fd.sync()
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                return null
            }
            pruneCrashQueue(directory)
            finalFile
        } catch (e: Exception) {
            safeLogE("Failed to write crash record", e)
            tempFile.delete()
            null
        }
    }

    internal fun migrateLegacyCrashIfPresent(context: Context) {
        migrateLegacyCrashIfPresent(crashDirectory(context), crashFile(context), legacyCrashFile(context))
    }

    internal fun migrateLegacyCrashIfPresent(directory: File, vararg legacyFiles: File) {
        legacyFiles.distinctBy { it.absolutePath }.forEach { legacyFile ->
            if (!legacyFile.exists()) return@forEach
            try {
                val legacy = parseLegacyCrashRecord(legacyFile.readText())
                val record = CrashRecord(
                    id = UUID.randomUUID().toString(),
                    exceptionClass = legacy.exceptionClass,
                    message = legacy.message,
                    stackTrace = legacy.stackTrace.take(MAX_STACK_TRACE_CHARS),
                    threadName = legacy.threadName,
                    timestamp = legacy.timestamp,
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE.toLong(),
                    sdkInt = sdkInt(),
                    manufacturer = manufacturer(),
                    model = model(),
                    processName = currentProcessName(),
                    breadcrumbs = emptyList()
                )
                if (writeCrashRecord(directory, record) != null) {
                    legacyFile.delete()
                }
            } catch (e: Exception) {
                safeLogE("Failed to migrate legacy crash record", e)
                legacyFile.delete()
            }
        }
    }

    internal fun purgeQueuedCrashes(context: Context) {
        queuedCrashFiles(context).forEach { it.delete() }
    }

    internal fun resetForTesting() {
        synchronized(lock) {
            breadcrumbs.clear()
            installed = false
            previousHandler = null
        }
    }

    private fun isTelemetryEnabled(context: Context): Boolean {
        return runCatching { PreferenceManager(context.applicationContext).isTelemetryEnabled() }
            .getOrDefault(false)
    }

    private fun snapshotBreadcrumbs(): List<CrashBreadcrumb> {
        return synchronized(lock) { breadcrumbs.toList() }
    }

    private fun pruneCrashQueue(directory: File) {
        queuedCrashFiles(directory).dropLast(MAX_QUEUED_CRASHES).forEach { it.delete() }
    }

    private fun crashDirectory(context: Context): File {
        return File(protectedContext(context).filesDir, CRASH_DIR)
    }

    private fun crashFile(context: Context): File {
        return File(protectedContext(context).filesDir, LEGACY_CRASH_FILE)
    }

    private fun legacyCrashFile(context: Context): File {
        return File(context.applicationContext.filesDir, LEGACY_CRASH_FILE)
    }

    private fun protectedContext(context: Context): Context {
        return context.applicationContext.createDeviceProtectedStorageContext()
            ?: context.applicationContext
    }

    private fun currentProcessName(): String? {
        return runCatching {
            if (sdkInt() >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                null
            }
        }.getOrNull()
    }

    private fun sdkInt(): Int {
        return runCatching { Build.VERSION.SDK_INT }.getOrDefault(0)
    }

    private fun manufacturer(): String {
        return runCatching { Build.MANUFACTURER ?: "" }.getOrDefault("")
    }

    private fun model(): String {
        return runCatching { Build.MODEL ?: "" }.getOrDefault("")
    }

    private fun parseLegacyCrashRecord(json: String): LegacyCrashRecord {
        val legacy = JSONObject(json)
        return LegacyCrashRecord(
            exceptionClass = legacy.optString("exceptionClass"),
            message = legacy.optString("message").ifEmpty { null },
            stackTrace = legacy.optString("stackTrace"),
            threadName = legacy.optString("threadName"),
            timestamp = legacy.optLong("timestamp")
        )
    }

    private fun safeLogE(message: String, throwable: Throwable) {
        runCatching { Log.e(TAG, message, throwable) }
    }

}
