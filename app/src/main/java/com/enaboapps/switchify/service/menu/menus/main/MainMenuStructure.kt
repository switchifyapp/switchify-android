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
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.DeviceLockObserver

class MainMenuStructure(private val accessibilityService: SwitchifyAccessibilityService) {
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService)
    private val deviceLockObserver = DeviceLockObserver(accessibilityService)
    private val preferenceManager = PreferenceManager(accessibilityService)
    private val scanSettings = ScanSettings(accessibilityService)

    val deviceItem = MenuItem(
        id = "device",
        labelResource = R.string.menu_title_device,
        drawableId = R.drawable.ic_device,
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openDeviceMenu() }
    )

    /**
     * Builds the main menu structure dynamically based on current state.
     * This allows menu items to reflect the latest keyboard state and other conditions.
     */
    fun buildMainMenuObject() = MenuStructure(
        id = "main_menu",
        items = listOfNotNull(
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
        ),
        context = accessibilityService
    )

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
