package com.enaboapps.switchify.service.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.menu.MenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Manages user-curated favourite apps for quick launching from the accessibility service menu.
 * Favourite apps are stored as a JSON array of package names in SharedPreferences.
 */
class FavouriteAppsManager(private val context: Context) {

    private val preferenceManager = PreferenceManager(context)

    /**
     * Gets the list of favourite apps, resolving names via PackageManager.
     * Apps that are no longer installed are silently filtered out.
     * @return list of FavouriteApp objects for currently installed favourite apps
     */
    fun getFavouriteApps(): List<FavouriteApp> {
        val packageNames = getSavedPackageNames()
        val packageManager = context.packageManager
        return packageNames.mapNotNull { packageName ->
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                // Only include if the app is still launchable
                if (packageManager.getLaunchIntentForPackage(packageName) != null) {
                    FavouriteApp(
                        packageName = packageName,
                        appName = packageManager.getApplicationLabel(appInfo).toString()
                    )
                } else null
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    /**
     * Saves the ordered list of favourite app package names.
     * @param packageNames ordered list of package names to save
     */
    fun saveFavouriteApps(packageNames: List<String>) {
        val jsonArray = JSONArray(packageNames)
        preferenceManager.setStringValue(
            PreferenceManager.PREFERENCE_KEY_FAVOURITE_APPS,
            jsonArray.toString()
        )
    }

    /**
     * Adds an app to the favourites list.
     * @param packageName the package name of the app to add
     */
    fun addFavouriteApp(packageName: String) {
        val current = getSavedPackageNames().toMutableList()
        if (!current.contains(packageName)) {
            current.add(packageName)
            saveFavouriteApps(current)
        }
    }

    /**
     * Removes an app from the favourites list.
     * @param packageName the package name of the app to remove
     */
    fun removeFavouriteApp(packageName: String) {
        val current = getSavedPackageNames().toMutableList()
        current.remove(packageName)
        saveFavouriteApps(current)
    }

    /**
     * Creates a MenuItem for the given FavouriteApp that launches the app when activated.
     * @param app the FavouriteApp to create a menu item for
     * @return MenuItem configured to launch the app
     */
    fun createMenuItem(app: FavouriteApp): MenuItem {
        return MenuItem(
            id = "favourite_app_${app.packageName}",
            userProvidedText = app.appName,
            descriptionResource = R.string.menu_item_open_favourite_app_description,
            action = { launchApp(app.packageName) }
        )
    }

    /**
     * Retrieves all installed launchable apps on the device.
     * @return list of FavouriteApp objects sorted alphabetically by app name
     */
    suspend fun getAllLaunchableApps(): List<FavouriteApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
        resolveInfoList
            .filter { it.activityInfo.packageName != context.packageName }
            .map { resolveInfo ->
                FavouriteApp(
                    packageName = resolveInfo.activityInfo.packageName,
                    appName = resolveInfo.loadLabel(packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
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
     * Reads the saved package names from SharedPreferences.
     * @return list of package name strings
     */
    private fun getSavedPackageNames(): List<String> {
        val json = preferenceManager.getStringValue(
            PreferenceManager.PREFERENCE_KEY_FAVOURITE_APPS, "[]"
        )
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Data class representing a favourite app.
     * @property packageName the app's package identifier
     * @property appName the human-readable app name
     */
    data class FavouriteApp(
        val packageName: String,
        val appName: String
    )
}
