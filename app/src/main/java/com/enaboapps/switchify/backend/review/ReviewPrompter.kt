package com.enaboapps.switchify.backend.review

import android.app.Activity
import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import com.google.android.play.core.review.ReviewManager

object ReviewPrompter {
    private const val DEFAULT_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000

    fun shouldPrompt(
        context: Context,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val prefs = PreferenceManager(context)
        val last = prefs.getLongValue(PreferenceManager.PREFERENCE_KEY_REVIEW_LAST_SHOWN, 0L)
        return now - last >= cooldownMs
    }

    fun recordPrompt(context: Context, now: Long = System.currentTimeMillis()) {
        PreferenceManager(context).setLongValue(
            PreferenceManager.PREFERENCE_KEY_REVIEW_LAST_SHOWN,
            now
        )
    }

    fun requestIfDue(
        context: Context,
        reviewManager: ReviewManager,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ) {
        if (!shouldPrompt(context, cooldownMs)) return
        Logger.log(LogEvent.ReviewRequested)
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                Logger.log(LogEvent.ReviewLaunched)
                reviewManager.launchReviewFlow(context as Activity, reviewInfo)
                    .addOnCompleteListener {
                        Logger.log(LogEvent.ReviewCompleted)
                    }
            }
        }
        recordPrompt(context)
    }
}

