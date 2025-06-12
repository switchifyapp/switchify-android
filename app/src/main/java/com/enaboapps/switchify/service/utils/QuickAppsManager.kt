package com.enaboapps.switchify.service.utils

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import com.enaboapps.switchify.service.menu.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manager class for handling quick apps functionality
 */
class QuickAppsManager(private val context: Context) {
    
    /**
     * Check if usage stats permission is granted
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    /**
     * Open usage stats permission settings
     */
    fun openUsageStatsSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    /**
     * Preload apps with completion callback
     */
    fun preloadApps(completion: (List<RecentApp>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val apps = getRecentApps()
            launch(Dispatchers.Main) {
                completion(apps)
            }
        }
    }

    /**
     * Get recently used apps
     */
    suspend fun getRecentApps(): List<RecentApp> = withContext(Dispatchers.IO) {
        val apps = mutableListOf<RecentApp>()
        
        if (!hasUsageStatsPermission()) {
            return@withContext apps
        }
        
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val packageManager = context.packageManager

            // Get usage stats for all time
            val endTime = System.currentTimeMillis()
            val startTime = 0L // Get all usage data
            
            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            )

            // Group by package name and take the most recent entry for each app
            val recentApps = usageStatsList
                .filter { stats ->
                    stats.packageName != context.packageName && // Exclude our own app
                    stats.lastTimeUsed > 0 &&
                    isLaunchableApp(stats.packageName, packageManager)
                }
                .groupBy { it.packageName }
                .mapValues { entry ->
                    // Get the entry with the latest lastTimeUsed for each package
                    entry.value.maxByOrNull { it.lastTimeUsed }!!
                }
                .values
                .sortedByDescending { it.lastTimeUsed }
                .take(10) // Limit to 10 recent apps
            
            for (stats in recentApps) {
                try {
                    val appInfo = packageManager.getApplicationInfo(stats.packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    
                    apps.add(RecentApp(
                        packageName = stats.packageName,
                        appName = appName,
                        lastUsedTime = stats.lastTimeUsed
                    ))
                } catch (e: PackageManager.NameNotFoundException) {
                    // Skip apps that are no longer installed
                }
            }
        } catch (e: Exception) {
            // Handle any errors silently
        }
        
        return@withContext apps
    }
    
    /**
     * Convert RecentApp to MenuItem for menu display
     */
    fun createMenuItem(app: RecentApp): MenuItem {
        return MenuItem(
            id = "recent_app_${app.packageName}",
            userProvidedText = app.appName,
            action = {
                launchApp(app.packageName)
            }
        )
    }
    
    /**
     * Launch an app by package name
     */
    private fun launchApp(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }
    
    /**
     * Check if an app is launchable (has a launcher activity)
     */
    private fun isLaunchableApp(packageName: String, packageManager: PackageManager): Boolean {
        return packageManager.getLaunchIntentForPackage(packageName) != null
    }
    
    /**
     * Data class representing a recent app
     */
    data class RecentApp(
        val packageName: String,
        val appName: String,
        val lastUsedTime: Long
    )
}