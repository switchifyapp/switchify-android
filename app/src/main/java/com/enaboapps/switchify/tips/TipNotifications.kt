package com.enaboapps.switchify.tips

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.enaboapps.switchify.BuildConfig
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.MainActivity
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

object TipNotifications {
    private const val TAG = "TipNotifications"
    private const val TOPIC = "tips"
    private const val CHANNEL_ID = "tips"
    private const val FIREBASE_TIMEOUT_SECONDS = 30L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registrationMutex = Mutex()

    fun isAvailable(): Boolean {
        return listOf(
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID
        ).none { it.isBlank() }
    }

    fun init(context: Context) {
        reconcileRegistration(context.applicationContext)
    }

    fun isEnabled(context: Context): Boolean {
        return isAvailable() && TipPreferences(context).enabled
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        TipPreferences(context).enabled = enabled
        reconcileRegistration(context.applicationContext)
    }

    fun onNewToken(context: Context) {
        TipPreferences(context).apply {
            registrationMayExist = true
            subscribed = false
        }
        reconcileRegistration(context.applicationContext)
    }

    fun canPostNotifications(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, tip: TipMessage) {
        val appContext = context.applicationContext
        if (!canPostNotifications(appContext)) return
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
        if (!isAvailable()) return
        val preferences = TipPreferences(context)
        if (!preferences.enabled && !preferences.registrationMayExist) return
        scope.launch {
            registrationMutex.withLock {
                try {
                    if (preferences.enabled) subscribe(context, preferences)
                    else removeRegistration(context, preferences)
                } catch (e: Exception) {
                    Log.w(TAG, "Tip registration is not up to date yet", e)
                }
            }
        }
    }

    private fun subscribe(context: Context, preferences: TipPreferences) {
        if (preferences.subscribed || !ensureFirebase(context)) return
        preferences.registrationMayExist = true
        await(FirebaseMessaging.getInstance().subscribeToTopic(TOPIC))
        preferences.subscribed = true
    }

    private fun removeRegistration(context: Context, preferences: TipPreferences) {
        if (!preferences.registrationMayExist || !ensureFirebase(context)) return
        preferences.subscribed = false
        val messaging = FirebaseMessaging.getInstance()
        await(messaging.unsubscribeFromTopic(TOPIC))
        await(messaging.deleteToken())
        await(FirebaseInstallations.getInstance().delete())
        preferences.registrationMayExist = false
    }

    private fun <T> await(task: Task<T>): T {
        return Tasks.await(task, FIREBASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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
