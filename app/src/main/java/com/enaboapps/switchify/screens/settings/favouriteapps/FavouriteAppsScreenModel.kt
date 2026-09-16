package com.enaboapps.switchify.screens.settings.favouriteapps

import android.content.Context
import androidx.lifecycle.ViewModel
import com.enaboapps.switchify.service.utils.FavouriteAppsManager
import com.enaboapps.switchify.service.utils.FavouriteAppsManager.FavouriteApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavouriteAppsScreenModel(context: Context) : ViewModel() {

    private val favouriteAppsManager = FavouriteAppsManager(context)

    private val _favouriteApps = MutableStateFlow<List<FavouriteApp>>(emptyList())
    val favouriteApps: StateFlow<List<FavouriteApp>> = _favouriteApps.asStateFlow()

    init {
        loadFavouriteApps()
    }

    /**
     * Loads the current list of favourite apps from storage.
     */
    private fun loadFavouriteApps() {
        _favouriteApps.value = favouriteAppsManager.getFavouriteApps()
    }

    /**
     * Adds an app to the favourites list.
     */
    fun addApp(packageName: String) {
        favouriteAppsManager.addFavouriteApp(packageName)
        loadFavouriteApps()
    }

    /**
     * Removes an app from the favourites list.
     */
    fun removeApp(app: FavouriteApp) {
        favouriteAppsManager.removeFavouriteApp(app.packageName)
        loadFavouriteApps()
    }

    /**
     * Reorders the favourite apps list when an item is moved.
     */
    fun reorderApps(fromIndex: Int, toIndex: Int) {
        val currentList = _favouriteApps.value.toMutableList()
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        _favouriteApps.value = currentList
        favouriteAppsManager.saveFavouriteApps(currentList.map { it.packageName })
    }
}
