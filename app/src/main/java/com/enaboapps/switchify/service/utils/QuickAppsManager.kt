package com.enaboapps.switchify.service.utils

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import com.enaboapps.switchify.service.menu.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages recently used apps functionality by querying usage statistics
 * and providing launchable menu items for quick app access.
 */
class QuickAppsManager(private val context: Context) {

    /**
     * Checks if the app has usage statistics permission.
     * @return true if permission is granted, false otherwise
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
     * Opens the system settings page for usage access permissions.
     */
    fun openUsageStatsSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Asynchronously loads recent apps and executes completion callback on main thread.
     * @param completion callback function that receives the list of recent apps
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
     * Retrieves recently used apps from the last 7 days.
     * @return list of RecentApp objects sorted by last used time (most recent first)
     */
    suspend fun getRecentApps(): List<RecentApp> = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) return@withContext emptyList()

        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val packageManager = context.packageManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (7L * 24 * 60 * 60 * 1000)

            return@withContext usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            )
                .filter {
                    it.packageName != context.packageName &&
                            it.lastTimeUsed > 0 &&
                            packageManager.getLaunchIntentForPackage(it.packageName) != null
                }
                .sortedByDescending { it.lastTimeUsed }
                .mapNotNull { stats ->
                    try {
                        val appInfo = packageManager.getApplicationInfo(stats.packageName, 0)
                        RecentApp(
                            packageName = stats.packageName,
                            appName = packageManager.getApplicationLabel(appInfo).toString(),
                            lastUsedTime = stats.lastTimeUsed
                        )
                    } catch (e: PackageManager.NameNotFoundException) { null }
                }
                .distinctBy { it.packageName }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    /**
     * Creates a MenuItem for the given RecentApp that launches the app when activated.
     * @param app the RecentApp to create a menu item for
     * @return MenuItem configured to launch the app
     */
    fun createMenuItem(app: RecentApp): MenuItem {
        return MenuItem(
            id = "recent_app_${app.packageName}",
            userProvidedText = app.appName,
            action = { launchApp(app.packageName) }
        )
    }

    /**
     * Launches an app by its package name.
     * @param packageName the package name of the app to launch
     */
    private fun launchApp(packageName: String) {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    /**
     * Data class representing a recently used app.
     * @property packageName the app's package identifier
     * @property appName the human-readable app name
     * @property lastUsedTime timestamp of when the app was last used
     */
    data class RecentApp(
        val packageName: String,
        val appName: String,
        val lastUsedTime: Long
    )
}