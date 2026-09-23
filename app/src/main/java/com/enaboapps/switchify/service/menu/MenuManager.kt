package com.enaboapps.switchify.service.menu

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.gestures.visuals.GestureTargetIndicatorController
import com.enaboapps.switchify.service.gestures.visuals.GestureTargetIndicatorOwner
import com.enaboapps.switchify.service.gestures.visuals.GestureTargetPoint
import com.enaboapps.switchify.service.menu.menus.ai.AiMenu
import com.enaboapps.switchify.service.menu.menus.actions.AccessibilityActionsMenu
import com.enaboapps.switchify.service.menu.menus.edit.EditMenu
import com.enaboapps.switchify.service.menu.menus.gestures.CustomGestureConfirmationMenu
import com.enaboapps.switchify.service.menu.menus.gestures.FingerModeMenu
import com.enaboapps.switchify.service.menu.menus.gestures.GesturePatternsMenu
import com.enaboapps.switchify.service.menu.menus.gestures.GesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.SwipeGesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.TapAndHoldGesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.TapGesturesMenu
import com.enaboapps.switchify.service.menu.menus.gestures.PinchGesturesMenu
import com.enaboapps.switchify.service.menu.menus.main.MainMenu
import com.enaboapps.switchify.service.menu.menus.media.MediaControlMenu
import com.enaboapps.switchify.service.menu.menus.favouriteapps.FavouriteAppsMenu
import com.enaboapps.switchify.service.menu.menus.switchprofiles.SwitchProfilesMenu
import com.enaboapps.switchify.service.menu.menus.switchprofiles.SwitchProfileConfirmationMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.menus.scroll.ScrollMenu
import com.enaboapps.switchify.service.menu.menus.settings.SettingsMenu
import com.enaboapps.switchify.service.menu.menus.system.DeviceMenu
import com.enaboapps.switchify.service.menu.menus.system.VolumeControlMenu
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.techniques.nodes.NodeActionTarget
import com.enaboapps.switchify.service.techniques.nodes.NodeActionLocator
import com.enaboapps.switchify.service.techniques.nodes.AccessibilityActionCoordinator
import com.enaboapps.switchify.service.techniques.nodes.AndroidNodeActionResolver
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

    private lateinit var gestureTargetIndicator: GestureTargetIndicatorController

    /**
     * The scanning manager
     */
    private var scanningManager: ScanningManager? = null

    /**
     * The accessibility service
     */
    private var accessibilityService: SwitchifyAccessibilityService? = null

    private var accessibilityActionCoordinator: AccessibilityActionCoordinator? = null

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
        accessibilityService: SwitchifyAccessibilityService,
        gestureTargetIndicator: GestureTargetIndicatorController
    ) {
        this.scanningManager = scanningManager
        menuHierarchy = MenuHierarchy(scanningManager)
        this.accessibilityService = accessibilityService
        this.gestureTargetIndicator = gestureTargetIndicator
        accessibilityActionCoordinator?.cancel()
        accessibilityActionCoordinator = AccessibilityActionCoordinator(
            scope = accessibilityService.getServiceScope(),
            resolver = AndroidNodeActionResolver(accessibilityService, accessibilityService),
            menuActions = AndroidAccessibilityActionMenuActions(
                openMenu = ::openResolvedAccessibilityActionsMenu,
                replaceMenu = ::replaceResolvedAccessibilityActionsMenu,
                showMain = ::rebuildMainMenuWithoutAccessibilityActions,
                closeMenus = ::closeMenuHierarchy
            )
        )
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
        val mainMenu = MainMenu(serviceOrNull() ?: return)
        openMenu(mainMenu.build())

    }

    /**
     * This function opens the device menu
     */
    fun openDeviceMenu() {
        val deviceMenu = DeviceMenu(serviceOrNull() ?: return)
        openMenu(deviceMenu.build())
    }

    /**
     * This function opens the AI menu
     */
    fun openAiMenu() {
        val aiMenu = AiMenu(serviceOrNull() ?: return)
        openMenu(aiMenu.build())
    }

    internal fun openAccessibilityActionsMenu(locator: NodeActionLocator) {
        accessibilityActionCoordinator?.open(locator)
    }

    internal fun selectAccessibilityAction(target: NodeActionTarget, actionId: Int) {
        accessibilityActionCoordinator?.select(target, actionId)
    }

    private fun openResolvedAccessibilityActionsMenu(target: NodeActionTarget) {
        val actionsMenu = AccessibilityActionsMenu(serviceOrNull() ?: return, target)
        openMenu(actionsMenu.build())
    }

    private fun replaceResolvedAccessibilityActionsMenu(target: NodeActionTarget) {
        val actionsMenu = AccessibilityActionsMenu(serviceOrNull() ?: return, target)
        menuHierarchy?.replaceTopMenu(actionsMenu.build())
    }

    private fun rebuildMainMenuWithoutAccessibilityActions() {
        val mainMenu = MainMenu(serviceOrNull() ?: return, includeAccessibilityActions = false)
        menuHierarchy?.replaceAllMenus(mainMenu.build())
    }

    internal fun cancelAccessibilityActionResolution() {
        accessibilityActionCoordinator?.cancel()
    }

    internal fun cleanupAccessibilityActions() {
        accessibilityActionCoordinator?.cancel()
        accessibilityActionCoordinator = null
    }

    internal fun cleanup() {
        cleanupAccessibilityActions()
        menuHierarchy?.dispose()
        menuHierarchy = null
        scanningManager = null
        accessibilityService = null
    }

    /**
     * Returns the bound accessibility service, or null once [cleanup] has run so
     * late menu requests no-op instead of crashing the service process.
     */
    private fun serviceOrNull(): SwitchifyAccessibilityService? {
        val service = accessibilityService
        if (service == null) {
            android.util.Log.w("MenuManager", "Ignoring menu request: accessibility service is not bound")
        }
        return service
    }

    /**
     * This function opens the edit menu
     */
    fun openEditMenu() {
        val editMenu = EditMenu(serviceOrNull() ?: return)
        openMenu(editMenu.build())
    }

    /**
     * This function opens the settings menu
     */
    fun openSettingsMenu() {
        val settingsMenu = SettingsMenu(serviceOrNull() ?: return)
        openMenu(settingsMenu.build())
    }

    /**
     * This function opens the volume control menu
     */
    fun openVolumeControlMenu() {
        val volumeControlMenu = VolumeControlMenu(serviceOrNull() ?: return)
        openMenu(volumeControlMenu.build())
    }

    /**
     * This function opens the gestures menu
     */
    fun openGesturesMenu() {
        val gesturesMenu = GesturesMenu(serviceOrNull() ?: return)
        openMenu(gesturesMenu.build())
    }

    /**
     * This function opens the gesture patterns menu
     */
    fun openGesturePatternsMenu() {
        val gesturePatternsMenu = GesturePatternsMenu(serviceOrNull() ?: return)
        openMenu(gesturePatternsMenu.build())
    }

    /**
     * This function opens the media control menu
     */
    fun openMediaControlMenu() {
        val mediaControlMenu = MediaControlMenu(serviceOrNull() ?: return)
        openMenu(mediaControlMenu.build())
    }

    /**
     * This function opens the scroll menu
     */
    fun openScrollMenu() {
        val scrollMenu = ScrollMenu(serviceOrNull() ?: return)
        openMenu(scrollMenu.build())
    }

    /**
     * This function opens the tap menu
     */
    fun openTapMenu() {
        val tapGesturesMenu = TapGesturesMenu(serviceOrNull() ?: return)
        openMenu(tapGesturesMenu.build())
    }

    /**
     * This function opens the tap and hold gestures menu
     */
    fun openTapAndHoldMenu() {
        val tapAndHoldGesturesMenu = TapAndHoldGesturesMenu(serviceOrNull() ?: return)
        openMenu(tapAndHoldGesturesMenu.build())
    }

    /**
     * This function opens the swipe gestures menu
     */
    fun openSwipeMenu() {
        val swipeGesturesMenu = SwipeGesturesMenu(serviceOrNull() ?: return)
        openMenu(swipeGesturesMenu.build())
    }

    /**
     * This function opens the pinch gestures menu
     */
    fun openPinchGesturesMenu() {
        val pinchGesturesMenu = PinchGesturesMenu(serviceOrNull() ?: return)
        openMenu(pinchGesturesMenu.build())
    }

    /**
     * This function opens the custom gesture confirmation menu
     */
    fun openCustomGestureConfirmationMenu() {
        val customGestureConfirmationMenu = CustomGestureConfirmationMenu(serviceOrNull() ?: return)
        openMenu(customGestureConfirmationMenu.build())
    }

    /**
     * This function opens the finger mode selection menu
     */
    fun openFingerModeMenu() {
        val fingerModeMenu = FingerModeMenu(serviceOrNull() ?: return)
        openMenu(fingerModeMenu.build())
    }


    /**
     * This function opens the favourite apps menu with dynamic loading
     */
    fun openFavouriteAppsMenu() {
        val favouriteAppsMenu = FavouriteAppsMenu(serviceOrNull() ?: return)
        openMenu(favouriteAppsMenu.build())
    }

    fun openSwitchProfilesMenu() {
        val switchProfilesMenu = SwitchProfilesMenu(serviceOrNull() ?: return)
        openMenu(switchProfilesMenu.build())
    }

    fun openSwitchProfileConfirmationMenu(profileName: String) {
        val confirmationMenu = SwitchProfileConfirmationMenu(
            serviceOrNull() ?: return,
            profileName
        )
        openMenu(confirmationMenu.build())
    }

    fun dismissSwitchProfileConfirmationMenu() {
        if (getCurrentMenuView()?.menuId ==
            MenuConstants.MenuIds.SWITCH_PROFILE_CONFIRMATION_MENU
        ) {
            menuHierarchy?.popMenu()
        }
    }

    /**
     * This function opens the menu
     * @param menu The menu to open
     */
    private fun openMenu(menu: MenuView) {
        menuHierarchy?.let { hierarchy ->
            val point = GesturePoint.getPoint()
            gestureTargetIndicator.acquire(
                GestureTargetIndicatorOwner.MENU,
                GestureTargetPoint(point.x.toInt(), point.y.toInt())
            )
            hierarchy.openMenu(menu)
        }
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
        accessibilityActionCoordinator?.cancel()
        if (getCurrentMenuView()?.menuId ==
            MenuConstants.MenuIds.SWITCH_PROFILE_CONFIRMATION_MENU
        ) {
            ServiceCore.getSwitchProfileActivationCoordinator()?.cancel(
                reason = "menu_dismissed",
                dismissConfirmationMenu = false
            )
        }
        menuHierarchy?.removeAllMenus()

        gestureTargetIndicator.release(GestureTargetIndicatorOwner.MENU)
    }

    /**
     * Get the current menu view
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
        if (menuView.menuId == MenuConstants.MenuIds.ACCESSIBILITY_ACTIONS_MENU) {
            accessibilityActionCoordinator?.cancel()
        }
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
