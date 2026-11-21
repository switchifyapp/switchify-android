package com.enaboapps.switchify.tips

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TipMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (!PreferenceManager(this).isTipNotificationsEnabled()) return
        val tip = TipMessage.from(message.data) ?: return
        TipNotifications.show(this, tip)
    }

    override fun onNewToken(token: String) {
        TipNotifications.init(this)
    }
}
