package com.enaboapps.switchify.service.menu

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualManager
import com.enaboapps.switchify.service.menu.menus.edit.EditMenu
import com.enaboapps.switchify.service.menu.menus.gestures.CustomGestureConfirmationMenu
import com.enaboapps.switchify.service.menu.menus.gestures.GesturePatternsMenu
import com.enaboapps.switchify.service.menu.menus.gestures.GesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.SwipeGesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.TapGesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.ZoomGesturesMenu
import com.enaboapps.switchify.service.menu.menus.main.MainMenu
import com.enaboapps.switchify.service.menu.menus.media.MediaControlMenu
import com.enaboapps.switchify.service.menu.menus.quickapps.QuickAppsMenu
import com.enaboapps.switchify.service.menu.menus.scroll.ScrollMenu
import com.enaboapps.switchify.service.menu.menus.system.DeviceMenu
import com.enaboapps.switchify.service.menu.menus.system.VolumeControlMenu
import com.enaboapps.switchify.service.scanning.ScanningManager

/**
 * This class manages the menu
 */
class MenuManager {
    companion object {
        private var instance: MenuManager? = null

        /**
         * This function gets the instance of the menu manager
         */
        fun getInstance(): MenuManager {
            if (instance == null) {
                instance = MenuManager()
            }
            return instance!!
        }
    }

    /**
     * The current point visual
     */
    private lateinit var gestureVisualManager: GestureVisualManager

    /**
     * The scanning manager
     */
    private var scanningManager: ScanningManager? = null

    /**
     * The accessibility service
     */
    private var accessibilityService: SwitchifyAccessibilityService? = null

    /**
     * The menu hierarchy
     */
    var menuHierarchy: MenuHierarchy? = null

    /**
     * This function sets up the menu manager
     * @param scanningManager The scanning manager
     * @param accessibilityService The accessibility service
     */
    fun setup(
        scanningManager: ScanningManager,
        accessibilityService: SwitchifyAccessibilityService
    ) {
        this.scanningManager = scanningManager
        menuHierarchy = MenuHierarchy(scanningManager)
        this.accessibilityService = accessibilityService
        gestureVisualManager = GestureVisualManager(accessibilityService)
    }

    fun switchToPointScan() {
        scanningManager?.setPointScanType()
    }

    fun switchToRadar() {
        scanningManager?.setRadarType()
    }

    fun switchToItemScan() {
        scanningManager?.setItemScanType()
    }

    fun switchToDirectControl() {
        scanningManager?.setDirectControlType()
    }

    /**
     * This function opens the main menu
     */
    fun openMainMenu() {
        val mainMenu = MainMenu(accessibilityService!!)
        openMenu(mainMenu.build())

        // Show the current point visual
        gestureVisualManager.showStaticCircle(
            com.enaboapps.switchify.service.gestures.GesturePoint.x,
            com.enaboapps.switchify.service.gestures.GesturePoint.y
        )
    }

    /**
     * This function opens the device menu
     */
    fun openDeviceMenu() {
        val deviceMenu = DeviceMenu(accessibilityService!!)
        openMenu(deviceMenu.build())
    }

    /**
     * This function opens the edit menu
     */
    fun openEditMenu() {
        val editMenu = EditMenu(accessibilityService!!)
        openMenu(editMenu.build())
    }

    /**
     * This function opens the volume control menu
     */
    fun openVolumeControlMenu() {
        val volumeControlMenu = VolumeControlMenu(accessibilityService!!)
        openMenu(volumeControlMenu.build())
    }

    /**
     * This function opens the gestures menu
     */
    fun openGesturesMenu() {
        val gesturesMenu = GesturesMenu(accessibilityService!!)
        openMenu(gesturesMenu.build())
    }

    /**
     * This function opens the gesture patterns menu
     */
    fun openGesturePatternsMenu() {
        val gesturePatternsMenu = GesturePatternsMenu(accessibilityService!!)
        openMenu(gesturePatternsMenu.build())
    }

    /**
     * This function opens the media control menu
     */
    fun openMediaControlMenu() {
        val mediaControlMenu = MediaControlMenu(accessibilityService!!)
        openMenu(mediaControlMenu.build())
    }

    /**
     * This function opens the scroll menu
     */
    fun openScrollMenu() {
        val scrollMenu = ScrollMenu(accessibilityService!!)
        openMenu(scrollMenu.build())
    }

    /**
     * This function opens the tap menu
     */
    fun openTapMenu() {
        val tapGesturesMenu = TapGesturesMenu(accessibilityService!!)
        openMenu(tapGesturesMenu.build())
    }

    /**
     * This function opens the swipe gestures menu
     */
    fun openSwipeMenu() {
        val swipeGesturesMenu = SwipeGesturesMenu(accessibilityService!!)
        openMenu(swipeGesturesMenu.build())
    }

    /**
     * This function opens the zoom gestures menu
     */
    fun openZoomGesturesMenu() {
        val zoomGesturesMenu = ZoomGesturesMenu(accessibilityService!!)
        openMenu(zoomGesturesMenu.build())
    }

    /**
     * This function opens the custom gesture confirmation menu
     */
    fun openCustomGestureConfirmationMenu() {
        val customGestureConfirmationMenu = CustomGestureConfirmationMenu(accessibilityService!!)
        openMenu(customGestureConfirmationMenu.build())
    }


    /**
     * This function opens the quick apps menu with dynamic loading
     */
    fun openQuickAppsMenu() {
        val quickAppsMenu = QuickAppsMenu(accessibilityService!!)
        openMenu(quickAppsMenu.build())
    }

    /**
     * This function opens the menu
     * @param menu The menu to open
     */
    private fun openMenu(menu: MenuView) {
        // Add the menu to the hierarchy
        menuHierarchy?.openMenu(menu)
    }

    /**
     * This function replaces the current menu with a new menu with a delay
     * @param menu The new menu to replace the current menu with
     */
    fun replaceCurrentMenu(menu: MenuView) {
        menuHierarchy?.popMenu()
        
        // Wait 300ms before opening the new menu
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            menuHierarchy?.openMenu(menu)
        }, 300)
    }

    /**
     * This function closes the menu hierarchy
     */
    fun closeMenuHierarchy() {
        menuHierarchy?.removeAllMenus()

        // Hide the current point visual
        gestureVisualManager.hideCircle()
    }
}
