package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.database.MenuConfigurationRepository
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuActionResolver
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import kotlinx.coroutines.CoroutineScope

class MainMenuStructure(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val coroutineScope: CoroutineScope
) {
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService, coroutineScope)
    private val deviceLockObserver = DeviceLockObserver(accessibilityService)
    private val preferenceManager = PreferenceManager(accessibilityService)
    private val scanSettings = ScanSettings(accessibilityService)
    private val repository = MenuConfigurationRepository(accessibilityService)

    val deviceItem = MenuItem(
        id = "device",
        labelResource = R.string.menu_title_device,
        drawableId = R.drawable.ic_device,
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openDeviceMenu() }
    )

    /**
     * Constructs the main menu structure based on current runtime state.
     *
     * The returned menu reflects current conditions such as keyboard visibility,
     * device lock state, access technique, camera permission, and gesture context.
     * Items include system navigation, scanning and technique switches, gesture and
     * media submenus, head-control toggle, quick apps, edit actions, pause, and
     * any user-added items from other menus.
     *
     * @return A MenuStructure representing the main menu configured for the current state.
     */
    fun buildMainMenuObject() = MenuStructure(
        id = MenuConstants.MenuIds.MAIN_MENU,
        items = buildDefaultItems() + buildUserAddedItems(),
        context = accessibilityService,
        coroutineScope = coroutineScope
    )

    /**
     * Builds the default menu items for the main menu.
     */
    private fun buildDefaultItems() = listOfNotNull(
            // System navigation items - back and home
            MenuItemRegistry.getMainMenuDefinition("sys_back")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GlobalActionManager.goBack() }
                )
            },
            MenuItemRegistry.getMainMenuDefinition("sys_home")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GlobalActionManager.goHome() }
                )
            },
            // Show "Scan Keyboard" menu item when keyboard is visible but user has escaped
            if (KeyboardManager.shouldShowScanKeyboardMenuItem()) {
                MenuItemRegistry.getMainMenuDefinition("scan_keyboard")?.let { def ->
                    MenuItem(
                        definition = def,
                        action = {
                            KeyboardManager.returnToKeyboard()
                            MenuManager.getInstance().closeMenuHierarchy()
                        }
                    )
                }
            } else null,
            gestureMenuStructure.tapMenuItem,
            MenuItemRegistry.getMainMenuDefinition("gestures")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openGesturesMenu() }
                )
            },
            MenuItemRegistry.getMainMenuDefinition("scroll")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openScrollMenu() }
                )
            },
            if (deviceLockObserver.isUserUnlocked() == true) {
                MenuItemRegistry.getMainMenuDefinition("quick_apps")?.let { def ->
                    MenuItem(
                        definition = def,
                        isLinkToMenu = true,
                        action = { MenuManager.getInstance().openQuickAppsMenu() }
                    )
                }
            } else null,
            if (deviceLockObserver.isUserUnlocked() == true) {
                MenuItemRegistry.getMainMenuDefinition("gesture_patterns")?.let { def ->
                    MenuItem(
                        definition = def,
                        isLinkToMenu = true,
                        action = { MenuManager.getInstance().openGesturePatternsMenu() }
                    )
                }
            } else null,
            deviceItem,
            MenuItemRegistry.getMainMenuDefinition("media_control")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openMediaControlMenu() }
                )
            },
            if (NodeExaminer.canPerformEditActions(GesturePoint.getPoint())) {
                MenuItemRegistry.getMainMenuDefinition("edit")?.let { def ->
                    MenuItem(
                        definition = def,
                        isLinkToMenu = true,
                        action = { MenuManager.getInstance().openEditMenu() }
                    )
                }
            } else null,
            if (AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.ITEM_SCAN) {
                MenuItemRegistry.getMainMenuDefinition("switch_to_item_scan")?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { MenuManager.getInstance().switchToItemScan() }
                    )
                }
            } else null,
            if (AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.RADAR &&
                !scanSettings.isDirectionalScanMode()
            ) {
                MenuItemRegistry.getMainMenuDefinition("switch_to_radar")?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { MenuManager.getInstance().switchToRadar() }
                    )
                }
            } else null,
            if (AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.POINT_SCAN &&
                !scanSettings.isDirectionalScanMode()
            ) {
                MenuItemRegistry.getMainMenuDefinition("switch_to_point_scan")?.let { def ->
                    MenuItem(
                        definition = def,
                        action = { MenuManager.getInstance().switchToPointScan() }
                    )
                }
            } else null,
            // Head control toggle - only show if camera permission is granted
            if (CameraPermissionManager.getInstance(accessibilityService).hasPermission()) {
                MenuItemRegistry.getMainMenuDefinition("toggle_head_control")?.let { def ->
                    MenuItem(
                        definition = def,
                        action = {
                            val headControlService = ServiceCore.getHeadControlService()
                            val settings = HeadControlSettings(accessibilityService)
                            val currentlyEnabled = settings.isHeadControlEnabled()

                            // Try to toggle head control
                            val success = headControlService?.setEnabled(!currentlyEnabled) ?: false
                            if (success) {
                                // Only update settings if head control was successfully enabled/disabled
                                settings.setHeadControlEnabled(!currentlyEnabled)
                            }

                            // Close menu to show the effect
                            MenuManager.getInstance().closeMenuHierarchy()
                        }
                    )
                }
            } else null,
            MenuItemRegistry.getMainMenuDefinition("pause")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { ServiceCore.getPauseManager()?.startPause() }
                )
            }
        )

    /**
     * Builds menu items that were added by the user from other menus.
     * Uses MenuActionResolver to bind the appropriate actions for each item.
     */
    private fun buildUserAddedItems(): List<MenuItem> {
        return try {
            kotlinx.coroutines.runBlocking {
                val userAddedConfigs = repository.getUserAddedItems(MenuConstants.MenuIds.MAIN_MENU)

                userAddedConfigs.mapNotNull { config ->
                    val sourceMenuId = config.sourceMenuId ?: return@mapNotNull null
                    val definition = MenuItemRegistry.getDefinition(sourceMenuId, config.itemId)
                        ?: return@mapNotNull null

                    val action = MenuActionResolver.resolveAction(
                        sourceMenuId = sourceMenuId,
                        itemId = config.itemId,
                        accessibilityService = accessibilityService,
                        coroutineScope = coroutineScope
                    )

                    MenuItem(
                        definition = definition,
                        action = action
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainMenuStructure", "Error loading user-added items", e)
            emptyList()
        }
    }

    val menuManipulatorItems = listOfNotNull(
        MenuItem(
            id = "close_menu",
            drawableId = R.drawable.ic_close_menu,
            labelResource = R.string.menu_item_close_menu,
            showLabelAsDescription = false,
            isSmall = true,
            isMenuHierarchyManipulator = true,
            action = { MenuManager.getInstance().closeMenuHierarchy() }
        )
    )
} 