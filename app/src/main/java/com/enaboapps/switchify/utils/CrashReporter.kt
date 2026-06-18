package com.enaboapps.switchify.utils

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.google.gson.Gson
import java.io.File

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val CRASH_FILE = "pending_crash.json"
    private val gson = Gson()

    private data class CrashRecord(
        val exceptionClass: String,
        val message: String?,
        val stackTrace: String,
        val threadName: String,
        val timestamp: Long
    )

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val record = CrashRecord(
                    exceptionClass = throwable.javaClass.name,
                    message = throwable.message,
                    stackTrace = throwable.stackTraceToString(),
                    threadName = thread.name,
                    timestamp = System.currentTimeMillis()
                )
                crashFile(context).writeText(gson.toJson(record))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash record", e)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun uploadPendingCrashIfPresent(context: Context) {
        uploadCrashFile(crashFile(context))

        if (DeviceLockObserver.isUserUnlocked(context)) {
            val protectedFile = crashFile(context)
            val legacyFile = legacyCrashFile(context)
            if (legacyFile.absolutePath != protectedFile.absolutePath) {
                uploadCrashFile(legacyFile)
            }
        }
    }

    private fun uploadCrashFile(file: File) {
        if (!file.exists()) return
        try {
            val record = gson.fromJson(file.readText(), CrashRecord::class.java)
            Logger.log(
                event = LogEvent.UnhandledCrash,
                data = mapOf(
                    "exceptionClass" to record.exceptionClass,
                    "message" to (record.message ?: ""),
                    "threadName" to record.threadName,
                    "stackTrace" to record.stackTrace,
                    "crashTimestamp" to record.timestamp
                ),
                throwable = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload pending crash", e)
        } finally {
            file.delete()
        }
    }

    private fun crashFile(context: Context): File {
        val protectedContext = context.applicationContext.createDeviceProtectedStorageContext()
            ?: context.applicationContext
        return File(protectedContext.filesDir, CRASH_FILE)
    }

    private fun legacyCrashFile(context: Context): File {
        return File(context.applicationContext.filesDir, CRASH_FILE)
    }
}
