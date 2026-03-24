package com.enaboapps.switchify.service.menu

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualManager
import com.enaboapps.switchify.service.menu.menus.edit.EditMenu
import com.enaboapps.switchify.service.menu.menus.gestures.CustomGestureConfirmationMenu
import com.enaboapps.switchify.service.menu.menus.gestures.FingerModeMenu
import com.enaboapps.switchify.service.menu.menus.gestures.GesturePatternsMenu
import com.enaboapps.switchify.service.menu.menus.gestures.GesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.SwipeGesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.TapGesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.PinchGesturesMenu
import com.enaboapps.switchify.service.menu.menus.main.MainMenu
import com.enaboapps.switchify.service.menu.menus.media.MediaControlMenu
import com.enaboapps.switchify.service.menu.menus.favouriteapps.FavouriteAppsMenu
import com.enaboapps.switchify.service.menu.menus.scroll.ScrollMenu
import com.enaboapps.switchify.service.menu.menus.system.DeviceMenu
import com.enaboapps.switchify.service.menu.menus.system.VolumeControlMenu
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger

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
     * List of menu state observers
     */
    private val menuStateObservers = mutableListOf<MenuStateObserver>()

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
     * This function opens the pinch gestures menu
     */
    fun openPinchGesturesMenu() {
        val pinchGesturesMenu = PinchGesturesMenu(accessibilityService!!)
        openMenu(pinchGesturesMenu.build())
    }

    /**
     * This function opens the custom gesture confirmation menu
     */
    fun openCustomGestureConfirmationMenu() {
        val customGestureConfirmationMenu = CustomGestureConfirmationMenu(accessibilityService!!)
        openMenu(customGestureConfirmationMenu.build())
    }

    /**
     * This function opens the finger mode selection menu
     */
    fun openFingerModeMenu() {
        val fingerModeMenu = FingerModeMenu(accessibilityService!!)
        openMenu(fingerModeMenu.build())
    }


    /**
     * This function opens the favourite apps menu with dynamic loading
     */
    fun openFavouriteAppsMenu() {
        val favouriteAppsMenu = FavouriteAppsMenu(accessibilityService!!)
        openMenu(favouriteAppsMenu.build())
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

    /**
     * Get the current menu view for head control navigation
     * @return The current menu view or null if no menu is open
     */
    fun getCurrentMenuView(): MenuView? {
        return menuHierarchy?.getTopMenu()
    }

    /**
     * Register a menu state observer
     * @param observer The observer to register
     */
    fun registerMenuStateObserver(observer: MenuStateObserver) {
        if (!menuStateObservers.contains(observer)) {
            menuStateObservers.add(observer)
        }
    }

    /**
     * Unregister a menu state observer
     * @param observer The observer to unregister
     */
    fun unregisterMenuStateObserver(observer: MenuStateObserver) {
        menuStateObservers.remove(observer)
    }

    /**
     * Notify observers that a menu was opened
     */
    internal fun notifyMenuOpened(menuView: MenuView) {
        Logger.log(
            LogEvent.MenuOpened,
            data = mapOf(
                "result" to "success",
                "menu_id" to menuView.menuId
            )
        )
        menuStateObservers.forEach { observer ->
            try {
                observer.onMenuOpened(menuView)
            } catch (e: Exception) {
                // Log error but don't let one observer break others
                android.util.Log.e("MenuManager", "Error notifying observer of menu opened", e)
                Logger.log(
                    LogEvent.MenuObserverNotifyFailed,
                    data = mapOf(
                        "result" to "failure",
                        "reason" to "on_menu_opened_exception",
                        "menu_id" to menuView.menuId,
                        "observer" to observer::class.java.simpleName
                    ),
                    throwable = e
                )
            }
        }
    }

    /**
     * Notify observers that a menu was closed
     */
    internal fun notifyMenuClosed(menuView: MenuView) {
        Logger.log(
            LogEvent.MenuClosed,
            data = mapOf(
                "result" to "success",
                "menu_id" to menuView.menuId
            )
        )
        menuStateObservers.forEach { observer ->
            try {
                observer.onMenuClosed(menuView)
            } catch (e: Exception) {
                android.util.Log.e("MenuManager", "Error notifying observer of menu closed", e)
                Logger.log(
                    LogEvent.MenuObserverNotifyFailed,
                    data = mapOf(
                        "result" to "failure",
                        "reason" to "on_menu_closed_exception",
                        "menu_id" to menuView.menuId,
                        "observer" to observer::class.java.simpleName
                    ),
                    throwable = e
                )
            }
        }
    }

    /**
     * Notify observers that menu nodes changed
     */
    internal fun notifyMenuNodesChanged(menuView: MenuView) {
        menuStateObservers.forEach { observer ->
            try {
                observer.onMenuNodesChanged(menuView)
            } catch (e: Exception) {
                android.util.Log.e(
                    "MenuManager",
                    "Error notifying observer of menu nodes changed",
                    e
                )
                Logger.log(
                    LogEvent.MenuObserverNotifyFailed,
                    data = mapOf(
                        "result" to "failure",
                        "reason" to "on_menu_nodes_changed_exception",
                        "menu_id" to menuView.menuId,
                        "observer" to observer::class.java.simpleName
                    ),
                    throwable = e
                )
            }
        }
    }

    /**
     * Notify observers that all menus were closed
     */
    internal fun notifyAllMenusClosed() {
        menuStateObservers.forEach { observer ->
            try {
                observer.onAllMenusClosed()
            } catch (e: Exception) {
                android.util.Log.e("MenuManager", "Error notifying observer of all menus closed", e)
                Logger.log(
                    LogEvent.MenuObserverNotifyFailed,
                    data = mapOf(
                        "result" to "failure",
                        "reason" to "on_all_menus_closed_exception",
                        "observer" to observer::class.java.simpleName
                    ),
                    throwable = e
                )
            }
        }
    }
}
