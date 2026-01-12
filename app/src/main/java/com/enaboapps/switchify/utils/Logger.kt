package com.enaboapps.switchify.utils

import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Logger class for logging events to Timberlogs.
 */
object Logger {
    private const val TAG = "SwitchifyLogger"
    private const val TIMBERLOGS_URL = "https://timberlogs-ingest.enaboapps.workers.dev/v1/logs"

    private val scope = CoroutineScope(Dispatchers.IO)
    private val gson = Gson()

    private data class LogEntry(
        val level: String,
        val message: String,
        val source: String,
        val environment: String
    )

    private data class LogPayload(
        val logs: List<LogEntry>
    )

    /**
     * Logs a predefined event from LogEvent sealed class.
     *
     * @param event The predefined event from LogEvent sealed class.
     */
    fun log(event: LogEvent) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Event logged: ${event.eventName}")
        }

        scope.launch {
            try {
                val entry = LogEntry(
                    level = "info",
                    message = event.eventName,
                    source = "switchify-android",
                    environment = if (BuildConfig.DEBUG) "development" else "production"
                )
                val payload = LogPayload(logs = listOf(entry))

                val json = gson.toJson(payload)

                val url = URL(TIMBERLOGS_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.TIMBERLOGS_API_KEY}")
                connection.doOutput = true

                connection.outputStream.use { os ->
                    os.write(json.toByteArray())
                }

                val responseCode = connection.responseCode
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Timberlogs response: $responseCode")
                }

                connection.disconnect()
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to log event: ${e.message}", e)
                }
            }
        }
    }
}
