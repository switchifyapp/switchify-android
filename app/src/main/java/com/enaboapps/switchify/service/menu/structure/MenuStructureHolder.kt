package com.enaboapps.switchify.service.menu.structure

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.edit.EditMenuStructure
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.menus.main.MainMenuStructure
import com.enaboapps.switchify.service.menu.menus.media.MediaMenuStructure
import com.enaboapps.switchify.service.menu.menus.quickapps.QuickAppsMenuStructure
import com.enaboapps.switchify.service.menu.menus.scroll.ScrollMenuStructure
import com.enaboapps.switchify.service.menu.menus.system.SystemMenuStructure

/**
 * This class serves as a coordinator for all menu structures in the application.
 * It provides access to menu items and structures from different parts of the application
 * while maintaining a clean separation of concerns.
 */
class MenuStructureHolder(accessibilityService: SwitchifyAccessibilityService) {
    private val mainMenuStructure = MainMenuStructure(accessibilityService)
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService)
    private val systemMenuStructure = SystemMenuStructure(accessibilityService)
    private val mediaMenuStructure = MediaMenuStructure(accessibilityService)
    private val scrollMenuStructure = ScrollMenuStructure(accessibilityService)
    private val editMenuStructure = EditMenuStructure()
    private val quickAppsMenuStructure = QuickAppsMenuStructure(accessibilityService)

    // Main Menu
    val mainMenuObject = mainMenuStructure.mainMenuObject
    val menuManipulatorItems = mainMenuStructure.menuManipulatorItems

    // Gesture Menus
    val gesturesMenuObject = gestureMenuStructure.gesturesMenuObject
    val zoomGesturesMenuObject = gestureMenuStructure.zoomGesturesMenuObject
    val swipeGesturesMenuObject = gestureMenuStructure.swipeGesturesMenuObject
    val tapGesturesMenuObject = gestureMenuStructure.tapGesturesMenuObject
    val customGestureConfirmationMenuObject =
        gestureMenuStructure.customGestureConfirmationMenuObject

    // System Menus
    fun buildDeviceMenuObject() = systemMenuStructure.buildDeviceMenuObject()
    fun buildVolumeControlMenuObject() = systemMenuStructure.buildVolumeControlMenuObject()

    // Media Menu
    val mediaControlMenuObject = mediaMenuStructure.mediaControlMenuObject

    // Scroll Menu
    val scrollMenuObject = scrollMenuStructure.scrollMenuObject

    // Edit Menu
    fun buildEditMenuObject() = editMenuStructure.buildEditMenuObject()
    
    // Quick Apps Menu  
    fun buildQuickAppsMenuObject() = quickAppsMenuStructure.buildQuickAppsMenuObject()
}
