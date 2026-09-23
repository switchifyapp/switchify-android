package com.enaboapps.switchify.service.utils

import android.content.Context
import android.content.Intent

class AppLauncher(private val context: Context) {
    fun label(packageName: String): String = AppLabelResolver(context).label(packageName)

    fun launch(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
