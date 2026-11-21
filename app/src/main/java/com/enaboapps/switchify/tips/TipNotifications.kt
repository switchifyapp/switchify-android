package com.enaboapps.switchify.tips

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.MainActivity
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object TipNotifications {
    private const val TAG = "TipNotifications"
    private const val TOPIC = "tips"
    private const val CHANNEL_ID = "tips"
    private const val TOPIC_TIMEOUT_SECONDS = 30L
    private const val REMOVAL_TIMEOUT_SECONDS = 120L
    private const val FIRST_RETRY_DELAY_MILLIS = 60_000L
    private const val MAX_RETRY_DELAY_MILLIS = 6L * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registrationMutex = Mutex()
    private val retryOnNetworkRegistered = AtomicBoolean(false)
    private val followUpLock = Any()
    private var refreshJob: Job? = null
    private var retryJob: Job? = null
    private var failedAttempts = 0

    fun isAvailable(context: Context): Boolean {
        return isConfigured() && hasPlayServices(context.applicationContext)
    }

    private fun isConfigured(): Boolean {
        return listOf(
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID
        ).none { it.isBlank() }
    }

    private fun hasPlayServices(context: Context): Boolean {
        return runCatching {
            GoogleApiAvailabilityLight.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }

    fun init(context: Context) {
        reconcileRegistration(context.applicationContext)
    }

    fun isEnabled(context: Context): Boolean {
        return isAvailable(context) && TipPreferences(context).enabled
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        TipPreferences(context).enabled = enabled
        reconcileRegistration(context.applicationContext)
    }

    fun onNewToken(context: Context) {
        TipPreferences(context).apply {
            registrationMayExist = true
            subscribedAtMillis = 0L
        }
        reconcileRegistration(context.applicationContext)
    }

    fun canPostNotifications(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canShowTips(context: Context): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
        return notificationManager.areNotificationsEnabled() &&
                channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(context: Context, tip: TipMessage) {
        val appContext = context.applicationContext
        if (!canShowTips(appContext)) return
        val notificationManager = NotificationManagerCompat.from(appContext)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.tips_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = appContext.getString(R.string.tips_notification_channel_description)
            }
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tip)
            .setContentTitle(tip.title)
            .setContentText(tip.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tip.body))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(appContext, tip))
            .build()
        try {
            notificationManager.notify(notificationId(tip), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission was revoked before the tip could be shown", e)
        }
    }

    private fun contentIntent(context: Context, tip: TipMessage): PendingIntent {
        val intent = tip.url
            ?.let { Intent(Intent.ACTION_VIEW, it.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        return PendingIntent.getActivity(
            context,
            notificationId(tip),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationId(tip: TipMessage): Int = (tip.id ?: tip.title).hashCode()

    private fun reconcileRegistration(context: Context) {
        if (!isConfigured()) return
        val reconciler = TipRegistrationReconciler(TipPreferences(context), BuildConfig.VERSION_CODE)
        if (!reconciler.needsWork()) {
            scheduleRefresh(context, reconciler)
            return
        }
        scope.launch {
            registrationMutex.withLock {
                try {
                    if (reconciler.needsWork() && ensureFirebase(context)) {
                        reconciler.reconcile(FirebaseRegistrationClient { reconcileRegistration(context) })
                    }
                    cancelRetry()
                    scheduleRefresh(context, reconciler)
                } catch (e: Exception) {
                    Log.w(TAG, "Tip registration is not up to date yet", e)
                    retryWhenNetworkReturns(context)
                    scheduleRetry(context)
                }
            }
        }
    }

    private fun scheduleRefresh(context: Context, reconciler: TipRegistrationReconciler) {
        synchronized(followUpLock) {
            refreshJob?.cancel()
            val wait = reconciler.millisUntilRefresh() ?: return
            refreshJob = scope.launch {
                delay(wait)
                reconcileRegistration(context)
            }
        }
    }

    private fun scheduleRetry(context: Context) {
        synchronized(followUpLock) {
            retryJob?.cancel()
            val wait = (FIRST_RETRY_DELAY_MILLIS shl failedAttempts.coerceAtMost(16))
                .coerceAtMost(MAX_RETRY_DELAY_MILLIS)
            failedAttempts++
            retryJob = scope.launch {
                delay(wait)
                reconcileRegistration(context)
            }
        }
    }

    private fun cancelRetry() {
        synchronized(followUpLock) {
            retryJob?.cancel()
            retryJob = null
            failedAttempts = 0
        }
    }

    private fun retryWhenNetworkReturns(context: Context) {
        if (!retryOnNetworkRegistered.compareAndSet(false, true)) return
        try {
            context.getSystemService(ConnectivityManager::class.java)
                .registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        reconcileRegistration(context)
                    }
                })
        } catch (e: Exception) {
            retryOnNetworkRegistered.set(false)
            Log.w(TAG, "Could not watch for the network to retry tip registration", e)
        }
    }

    private class FirebaseRegistrationClient(
        private val onLateCompletion: () -> Unit
    ) : TipRegistrationClient {

        override fun finishPendingDeletion() {
            operations.settle(DELETE_TOKEN, REMOVAL_TIMEOUT_SECONDS, onLateCompletion)
            operations.settle(DELETE_INSTALLATION, REMOVAL_TIMEOUT_SECONDS, onLateCompletion)
        }

        override fun subscribe() {
            operations.forget(UNSUBSCRIBE)
            operations.run(SUBSCRIBE, TOPIC_TIMEOUT_SECONDS, onLateCompletion) {
                FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
            }
        }

        override fun unsubscribe() {
            operations.forget(SUBSCRIBE)
            operations.run(UNSUBSCRIBE, TOPIC_TIMEOUT_SECONDS, onLateCompletion) {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(TOPIC)
            }
        }

        override fun deleteToken() {
            operations.run(DELETE_TOKEN, REMOVAL_TIMEOUT_SECONDS, onLateCompletion) {
                FirebaseMessaging.getInstance().deleteToken()
            }
        }

        override fun deleteInstallation() {
            operations.run(DELETE_INSTALLATION, REMOVAL_TIMEOUT_SECONDS, onLateCompletion) {
                FirebaseInstallations.getInstance().delete()
            }
        }

        companion object {
            private const val SUBSCRIBE = "subscribe"
            private const val UNSUBSCRIBE = "unsubscribe"
            private const val DELETE_TOKEN = "deleteToken"
            private const val DELETE_INSTALLATION = "deleteInstallation"

            private val operations = TrackedOperations<Task<Void>>(
                isComplete = { it.isComplete },
                await = { task, timeoutSeconds -> Tasks.await(task, timeoutSeconds, TimeUnit.SECONDS) },
                onLateCompletion = { task, callback -> task.addOnCompleteListener { callback() } }
            )
        }
    }

    private fun ensureFirebase(context: Context): Boolean {
        return try {
            if (!DeviceLockObserver.isUserUnlocked(context)) {
                false
            } else {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(
                        context,
                        FirebaseOptions.Builder()
                            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                            .setApiKey(BuildConfig.FIREBASE_API_KEY)
                            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                            .build()
                    )
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise Firebase", e)
            false
        }
    }
}
