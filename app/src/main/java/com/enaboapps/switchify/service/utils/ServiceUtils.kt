package com.enaboapps.switchify.service.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import androidx.core.net.toUri

class ServiceUtils {

    // Function to check if the accessibility service is enabled.
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val accessibilityServices =
            accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in accessibilityServices) {
            if (service.id.contains(context.packageName)) {
                return true
            }
        }
        return false
    }

    // Function to send the user to the accessibility settings.
    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }

    /**
     * Opens the pro upgrade screen using deep link.
     *
     * @param context The context to start the activity
     */
    fun openProUpgrade(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "https://switchify.app/pro".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage(context.packageName)
        }
        context.startActivity(intent)
    }

}