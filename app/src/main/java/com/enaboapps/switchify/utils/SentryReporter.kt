package com.enaboapps.switchify.utils

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import io.sentry.Breadcrumb
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User

object SentryReporter {
    private const val TAG = "SentryReporter"
    private const val MAX_EXTRA_CHARS = 8 * 1024

    private val lock = Any()
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!Logger.isTelemetryEnabled()) return
        start()
    }

    fun onDeviceUnlocked(context: Context) {
        init(context)
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) start() else stop()
    }

    fun report(
        event: LogEvent,
        data: Map<String, Any>,
        throwable: Throwable?,
        flowId: String?,
        stepIndex: Int?
    ) {
        if (!Sentry.isEnabled()) return
        try {
            if (isCapturable(event.level)) {
                capture(event, data, throwable, flowId, stepIndex)
            } else {
                Sentry.addBreadcrumb(breadcrumb(event, data))
            }
        } catch (e: Exception) {
            safeLogE("Failed to report ${event.eventName}", e)
        }
    }

    internal fun isCapturable(level: String): Boolean {
        return sentryLevel(level) >= SentryLevel.ERROR
    }

    internal fun sentryLevel(level: String): SentryLevel {
        return when (level.lowercase()) {
            "debug" -> SentryLevel.DEBUG
            "warn", "warning" -> SentryLevel.WARNING
            "error" -> SentryLevel.ERROR
            "fatal" -> SentryLevel.FATAL
            else -> SentryLevel.INFO
        }
    }

    internal fun stringifyExtra(value: Any): String {
        return value.toString().take(MAX_EXTRA_CHARS)
    }

    private fun start() {
        synchronized(lock) {
            val context = appContext ?: return
            if (Sentry.isEnabled()) return
            if (!isUserUnlocked(context)) return
            val dsn = BuildConfig.SENTRY_DSN
            if (dsn.isBlank()) return
            try {
                SentryAndroid.init(context) { options ->
                    options.dsn = dsn
                    options.environment = if (BuildConfig.DEBUG) "development" else "production"
                    options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                    options.isDebug = BuildConfig.DEBUG
                    options.isEnableUncaughtExceptionHandler = true
                    options.isAnrEnabled = true
                    options.isSendDefaultPii = false
                    options.isAttachScreenshot = false
                    options.isAttachViewHierarchy = false
                    options.isEnableUserInteractionBreadcrumbs = false
                    options.isEnableUserInteractionTracing = false
                    options.tracesSampleRate = 0.0
                    options.beforeSend = SentryOptions.BeforeSendCallback { sentryEvent, _ ->
                        if (!Logger.isTelemetryEnabled()) {
                            null
                        } else {
                            sentryEvent.user = currentUser()
                            sentryEvent
                        }
                    }
                }
            } catch (e: Exception) {
                safeLogE("Failed to initialise Sentry", e)
            }
        }
    }

    private fun stop() {
        synchronized(lock) {
            if (!Sentry.isEnabled()) return
            try {
                Sentry.close()
            } catch (e: Exception) {
                safeLogE("Failed to close Sentry", e)
            }
        }
    }

    private fun capture(
        event: LogEvent,
        data: Map<String, Any>,
        throwable: Throwable?,
        flowId: String?,
        stepIndex: Int?
    ) {
        val scopeCallback = ScopeCallback { scope ->
            scope.level = sentryLevel(event.level)
            scope.setTag("event", event.eventName)
            scope.setTag("dataset", event.dataset)
            event.tags.forEachIndexed { index, tag -> scope.setTag("tag_$index", tag) }
            flowId?.let { scope.setTag("flow_id", it) }
            stepIndex?.let { scope.setExtra("step_index", it.toString()) }
            data.forEach { (key, value) -> scope.setExtra(key, stringifyExtra(value)) }
        }
        if (throwable != null) {
            Sentry.captureException(throwable, scopeCallback)
        } else {
            Sentry.captureMessage(event.eventName, scopeCallback)
        }
    }

    private fun breadcrumb(event: LogEvent, data: Map<String, Any>): Breadcrumb {
        return Breadcrumb().apply {
            category = event.dataset
            message = event.eventName
            level = sentryLevel(event.level)
            data.forEach { (key, value) -> setData(key, stringifyExtra(value)) }
        }
    }

    private fun isUserUnlocked(context: Context): Boolean {
        return runCatching { DeviceLockObserver.isUserUnlocked(context) }.getOrDefault(false)
    }

    private fun currentUser(): User {
        return User().apply { id = Logger.currentUserId() }
    }

    private fun safeLogE(message: String, throwable: Throwable) {
        runCatching { Log.e(TAG, message, throwable) }
    }
}
