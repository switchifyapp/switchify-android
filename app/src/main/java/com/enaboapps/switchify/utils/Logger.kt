package com.enaboapps.switchify.utils

import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.auth.repository.AuthRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

object Logger {
    private const val TAG = "SwitchifyLogger"
    private const val TIMBERLOGS_URL = "https://timberlogs-ingest.enaboapps.workers.dev/v1/logs"
    private const val SOURCE = "switchify-android"

    private val scope = CoroutineScope(Dispatchers.IO)
    private val gson = Gson()

    private data class LogEntry(
        val level: String,
        val message: String,
        val source: String,
        val environment: String,
        val dataset: String? = null,
        val version: String? = null,
        val data: Map<String, Any>? = null,
        val errorName: String? = null,
        val errorStack: String? = null,
        val tags: List<String>? = null,
        val flowId: String? = null,
        val stepIndex: Int? = null,
        val userId: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private data class LogPayload(
        val logs: List<LogEntry>
    )

    fun log(
        event: LogEvent,
        data: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
        flowId: String? = null,
        stepIndex: Int? = null
    ) {
        val sanitizedData = data.filterValues { it != null }.mapValues { it.value as Any }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Event logged: ${event.eventName}")
        }

        scope.launch {
            try {
                val entry = LogEntry(
                    level = event.level,
                    message = event.eventName,
                    source = SOURCE,
                    environment = if (BuildConfig.DEBUG) "development" else "production",
                    dataset = event.dataset,
                    version = BuildConfig.VERSION_NAME,
                    data = sanitizedData.ifEmpty { null },
                    errorName = throwable?.javaClass?.simpleName,
                    errorStack = throwable?.stackTraceToString(),
                    tags = event.tags.ifEmpty { null },
                    flowId = flowId,
                    stepIndex = stepIndex,
                    userId = AuthRepository.instance.getCurrentUser()?.email
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
