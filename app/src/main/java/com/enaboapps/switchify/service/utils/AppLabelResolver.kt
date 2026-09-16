package com.enaboapps.switchify.service.utils

import android.content.Context
import com.enaboapps.switchify.R

class AppLabelResolver(private val context: Context) {
    fun label(packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        safeInstalledLabel(packageName, context.packageManager.getApplicationLabel(info),
            context.getString(R.string.unnamed_app))
    }.getOrElse { context.getString(R.string.unavailable_app) }

    companion object {
        internal fun safeInstalledLabel(packageName: String, label: CharSequence?, unnamed: String): String {
            val name = label?.toString()?.trim().orEmpty()
            return name.takeUnless { it.isBlank() || it == packageName || it.startsWith("$packageName.") }
                ?: unnamed
        }
    }
}
