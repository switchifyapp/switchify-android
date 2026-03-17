package com.enaboapps.switchify.utils

import android.content.Context
import android.util.Log
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
        val appContext = context.applicationContext
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
                val file = File(appContext.filesDir, CRASH_FILE)
                file.writeText(gson.toJson(record))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash record", e)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun uploadPendingCrashIfPresent(context: Context) {
        val file = File(context.filesDir, CRASH_FILE)
        if (!file.exists()) return
        try {
            val record = gson.fromJson(file.readText(), CrashRecord::class.java)
            // Pass throwable=null — the real stack trace is in data["stackTrace"].
            // userId may be null here since auth hasn't initialised yet on this launch; that's expected.
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
}
