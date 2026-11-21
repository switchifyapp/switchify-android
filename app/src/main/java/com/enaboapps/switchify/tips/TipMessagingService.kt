package com.enaboapps.switchify.tips

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TipMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val preferences = TipPreferences(this)
        if (!preferences.enabled) return
        val tip = TipMessage.from(message.data) ?: return
        if (!TipNotifications.canShowTips(this)) return
        if (tip.id != null && !preferences.markShown(tip.id)) return
        TipNotifications.show(this, tip)
    }

    override fun onNewToken(token: String) {
        TipNotifications.onNewToken(this)
    }
}
