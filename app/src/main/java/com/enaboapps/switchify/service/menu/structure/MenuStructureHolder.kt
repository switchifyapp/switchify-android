package com.enaboapps.switchify.service.menu.structure

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.edit.EditMenuStructure
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.menus.main.MainMenuStructure
import com.enaboapps.switchify.service.menu.menus.media.MediaMenuStructure
import com.enaboapps.switchify.service.menu.menus.favouriteapps.FavouriteAppsMenuStructure
import com.enaboapps.switchify.service.menu.menus.scroll.ScrollMenuStructure
import com.enaboapps.switchify.service.menu.menus.settings.SettingsMenuStructure
import com.enaboapps.switchify.service.menu.menus.system.SystemMenuStructure

/**
 * This class serves as a coordinator for all menu structures in the application.
 * It provides access to menu items and structures from different parts of the application
 * while maintaining a clean separation of concerns.
 */
class MenuStructureHolder(accessibilityService: SwitchifyAccessibilityService) {
    private val serviceScope = accessibilityService.getServiceScope()

    private val mainMenuStructure = MainMenuStructure(accessibilityService, serviceScope)
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService, serviceScope)
    private val systemMenuStructure = SystemMenuStructure(accessibilityService, serviceScope)
    private val mediaMenuStructure = MediaMenuStructure(accessibilityService, serviceScope)
    private val scrollMenuStructure = ScrollMenuStructure(accessibilityService, serviceScope)
    private val editMenuStructure = EditMenuStructure(accessibilityService, serviceScope)
    private val favouriteAppsMenuStructure = FavouriteAppsMenuStructure(accessibilityService, serviceScope)
    private val settingsMenuStructure = SettingsMenuStructure(accessibilityService, serviceScope)

    // Main Menu
    fun buildMainMenuObject() = mainMenuStructure.buildMainMenuObject()
    val menuManipulatorItems = mainMenuStructure.menuManipulatorItems

    // Gesture Menus
    val gesturesMenuObject = gestureMenuStructure.gesturesMenuObject
    val pinchGesturesMenuObject = gestureMenuStructure.pinchGesturesMenuObject
    val swipeGesturesMenuObject = gestureMenuStructure.swipeGesturesMenuObject
    val tapGesturesMenuObject = gestureMenuStructure.tapGesturesMenuObject
    val tapAndHoldGesturesMenuObject = gestureMenuStructure.tapAndHoldGesturesMenuObject
    val customGestureConfirmationMenuObject =
        gestureMenuStructure.customGestureConfirmationMenuObject
    val fingerModeMenuObject = gestureMenuStructure.fingerModeMenuObject

    // System Menus
    fun buildDeviceMenuObject() = systemMenuStructure.buildDeviceMenuObject()
    fun buildVolumeControlMenuObject() = systemMenuStructure.buildVolumeControlMenuObject()

    // Media Menu
    val mediaControlMenuObject = mediaMenuStructure.mediaControlMenuObject

    // Scroll Menu
    val scrollMenuObject = scrollMenuStructure.scrollMenuObject

    // Edit Menu
    fun buildEditMenuObject() = editMenuStructure.buildEditMenuObject()

    // Favourite Apps Menu
    fun buildFavouriteAppsMenuObject() = favouriteAppsMenuStructure.buildFavouriteAppsMenuObject()

    // Settings Menu
    fun buildSettingsMenuObject() = settingsMenuStructure.buildSettingsMenuObject()
}
