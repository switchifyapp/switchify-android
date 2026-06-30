package com.enaboapps.switchify.utils

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.auth.repository.AuthRepository
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal fun interface TimberlogsSender {
    fun send(json: String): Int
}

object Logger {
    private const val TAG = "SwitchifyLogger"
    private const val TIMBERLOGS_URL = "https://timberlogs-ingest.enaboapps.workers.dev/v1/logs"
    private const val SOURCE = "switchify-android"

    private val scope = CoroutineScope(Dispatchers.IO)
    private val gson = Gson()
    private var sender: TimberlogsSender = TimberlogsSender { json ->
        val url = URL(TIMBERLOGS_URL)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.TIMBERLOGS_API_KEY}")
            connection.doOutput = true

            connection.outputStream.use { os ->
                os.write(json.toByteArray())
            }

            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Holds the PreferenceManager used to read the telemetry opt-in flag. Set by
     * [init] from SwitchifyApplication.onCreate(). Until init is called, log() is a
     * no-op — safer than assuming telemetry is allowed.
     */
    private var preferenceManager: PreferenceManager? = null
    private var telemetryEnabledOverride: (() -> Boolean)? = null
    private var userIdOverride: (() -> String?)? = null

    /**
     * Wires up the telemetry opt-in gate. Must be called before any Logger.log(..)
     * invocation (in practice: first thing in SwitchifyApplication.onCreate()).
     */
    fun init(context: Context) {
        preferenceManager = PreferenceManager(context.applicationContext)
    }

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
        if (!isTelemetryEnabled()) {
            logSuppressed(event)
            return
        }
        CrashReporter.recordBreadcrumb(event)

        scope.launch {
            logNow(event, data, throwable, flowId, stepIndex, breadcrumbAlreadyRecorded = true)
        }
    }

    internal fun logNow(
        event: LogEvent,
        data: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
        flowId: String? = null,
        stepIndex: Int? = null
    ): Boolean {
        return logNow(event, data, throwable, flowId, stepIndex, breadcrumbAlreadyRecorded = false)
    }

    private fun logNow(
        event: LogEvent,
        data: Map<String, Any?>,
        throwable: Throwable?,
        flowId: String?,
        stepIndex: Int?,
        breadcrumbAlreadyRecorded: Boolean
    ): Boolean {
        val prefs = preferenceManager
        if (!isTelemetryEnabled(prefs)) {
            logSuppressed(event)
            return false
        }

        if (!breadcrumbAlreadyRecorded) {
            CrashReporter.recordBreadcrumb(event)
        }

        val sanitizedData = data.filterValues { it != null }.mapValues { it.value as Any }

        if (BuildConfig.DEBUG) {
            safeLogD("Event logged: ${event.eventName}")
        }

        return try {
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
                userId = userIdOverride?.invoke() ?: AuthRepository.instance.getCurrentUser()?.email ?: deviceUserId(prefs)
            )
            val payload = LogPayload(logs = listOf(entry))
            val responseCode = sender.send(gson.toJson(payload))
            if (BuildConfig.DEBUG) {
                safeLogD("Timberlogs response: $responseCode")
            }
            responseCode in 200..299
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                safeLogE("Failed to log event: ${e.message}", e)
            }
            false
        }
    }

    internal fun isTelemetryEnabled(): Boolean {
        return isTelemetryEnabled(preferenceManager)
    }

    private fun isTelemetryEnabled(prefs: PreferenceManager?): Boolean {
        return telemetryEnabledOverride?.invoke() ?: (prefs?.isTelemetryEnabled() == true)
    }

    private fun logSuppressed(event: LogEvent) {
        if (BuildConfig.DEBUG) {
            safeLogD("Suppressed (telemetry off): ${event.eventName}")
        }
    }

    private fun deviceUserId(prefs: PreferenceManager?): String {
        return prefs?.let { "device:${getOrCreateDeviceId(it)}" } ?: "device:unknown"
    }

    internal fun setSenderForTesting(testSender: TimberlogsSender) {
        sender = testSender
    }

    internal fun setTelemetryEnabledOverrideForTesting(provider: (() -> Boolean)?) {
        telemetryEnabledOverride = provider
    }

    internal fun setUserIdOverrideForTesting(provider: (() -> String?)?) {
        userIdOverride = provider
    }

    internal fun resetForTesting() {
        sender = TimberlogsSender { json ->
            val url = URL(TIMBERLOGS_URL)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.TIMBERLOGS_API_KEY}")
                connection.doOutput = true
                connection.outputStream.use { os ->
                    os.write(json.toByteArray())
                }
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
        telemetryEnabledOverride = null
        userIdOverride = null
    }

    /**
     * Returns a stable per-install identifier used as the userId fallback when no user
     * is signed in. Generated once on first call and persisted; the key is blacklisted
     * from sync so it never follows the account across devices.
     */
    private fun getOrCreateDeviceId(prefs: PreferenceManager): String {
        val existing = prefs.getStringValue(PreferenceManager.PREFERENCE_KEY_DEVICE_ID)
        if (existing.isNotEmpty()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.setStringValue(PreferenceManager.PREFERENCE_KEY_DEVICE_ID, generated)
        return generated
    }

    private fun safeLogD(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun safeLogE(message: String, throwable: Throwable) {
        runCatching { Log.e(TAG, message, throwable) }
    }
}
