package com.enaboapps.switchify.screens.settings.favouriteapps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.service.utils.FavouriteAppsManager
import com.enaboapps.switchify.service.utils.FavouriteAppsManager.FavouriteApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavouriteAppsScreenModel(context: Context) : ViewModel() {

    private val favouriteAppsManager = FavouriteAppsManager(context)

    private val _favouriteApps = MutableStateFlow<List<FavouriteApp>>(emptyList())
    val favouriteApps: StateFlow<List<FavouriteApp>> = _favouriteApps.asStateFlow()

    private val _allApps = MutableStateFlow<List<FavouriteApp>>(emptyList())
    val allApps: StateFlow<List<FavouriteApp>> = _allApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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
     * Loads all installed launchable apps for the picker dialog.
     */
    fun loadAllApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _allApps.value = favouriteAppsManager.getAllLaunchableApps()
            _isLoadingApps.value = false
        }
    }

    /**
     * Updates the search query for filtering apps in the picker.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Adds an app to the favourites list.
     */
    fun addApp(app: FavouriteApp) {
        favouriteAppsManager.addFavouriteApp(app.packageName)
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
