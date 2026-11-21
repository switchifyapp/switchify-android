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
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object TipNotifications {
    private const val TAG = "TipNotifications"
    private const val TOPIC = "tips"
    private const val CHANNEL_ID = "tips"

    private val lock = Any()

    fun isAvailable(): Boolean {
        return listOf(
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID
        ).none { it.isBlank() }
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (!ensureFirebase(appContext)) return
        if (!PreferenceManager(appContext).isTipNotificationsEnabled()) return
        if (!canPostNotifications(appContext)) return
        subscribe()
    }

    fun isEnabled(context: Context): Boolean {
        val appContext = context.applicationContext
        return isAvailable() &&
                PreferenceManager(appContext).isTipNotificationsEnabled() &&
                canPostNotifications(appContext)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        PreferenceManager(appContext).setTipNotificationsEnabled(enabled)
        if (!ensureFirebase(appContext)) return
        if (enabled) subscribe() else unsubscribe(appContext)
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
                NotificationManager.IMPORTANCE_DEFAULT
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
        val intent = tip.url?.let { Intent(Intent.ACTION_VIEW, it.toUri()) }
            ?: Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            notificationId(tip),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notificationId(tip: TipMessage): Int = (tip.id ?: tip.title).hashCode()

    private fun ensureFirebase(context: Context): Boolean {
        if (!isAvailable()) return false
        return synchronized(lock) {
            try {
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

    private fun subscribe() {
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC).addOnFailureListener { e ->
            Log.w(TAG, "Failed to subscribe to tips", e)
        }
    }

    private fun unsubscribe(context: Context) {
        val messaging = FirebaseMessaging.getInstance()
        messaging.unsubscribeFromTopic(TOPIC).addOnCompleteListener {
            if (!PreferenceManager(context).isTipNotificationsEnabled()) {
                messaging.deleteToken().addOnFailureListener { e ->
                    Log.w(TAG, "Failed to delete the messaging registration", e)
                }
            }
        }
    }
}
