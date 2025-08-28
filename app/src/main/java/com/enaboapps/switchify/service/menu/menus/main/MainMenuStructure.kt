package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.actions.GlobalActionManager
import com.enaboapps.switchify.service.scanning.ScanSettings

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

    val mainMenuObject = MenuStructure(
        id = "main_menu",
        items = listOfNotNull(
            // System navigation items - back and home
            MenuItem(
                id = "sys_back",
                drawableId = R.drawable.ic_sys_back,
                labelResource = R.string.system_back,
                action = { GlobalActionManager.goBack() }
            ),
            MenuItem(
                id = "sys_home",
                drawableId = R.drawable.ic_sys_home,
                labelResource = R.string.system_home,
                action = { GlobalActionManager.goHome() }
            ),
            // Show "Scan Keyboard" menu item when keyboard is visible but user has escaped
            if (KeyboardManager.shouldShowScanKeyboardMenuItem()) {
                MenuItem(
                    id = "scan_keyboard",
                    labelResource = R.string.menu_item_scan_keyboard,
                    drawableId = R.drawable.ic_scan_keyboard,
                    action = {
                        KeyboardManager.returnToKeyboard()
                        MenuManager.getInstance().closeMenuHierarchy()
                    }
                )
            } else null,
            gestureMenuStructure.tapMenuItem,
            MenuItem(
                id = "gestures",
                labelResource = R.string.menu_title_gestures,
                drawableId = R.drawable.ic_gestures,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openGesturesMenu() }
            ),
            MenuItem(
                id = "scroll",
                labelResource = R.string.menu_title_scroll,
                drawableId = R.drawable.ic_scroll,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openScrollMenu() }
            ),
            if (deviceLockObserver.isUserUnlocked() == true) {
                MenuItem(
                    id = "quick_apps",
                    labelResource = R.string.menu_title_quick_apps,
                    drawableId = R.drawable.ic_quick_apps,
                    isLinkToMenu = true,
                    action = {
                        MenuManager.getInstance().openQuickAppsMenu()
                    }
                )
            } else null,
            if (deviceLockObserver.isUserUnlocked() == true) {
                MenuItem(
                    id = "gesture_patterns",
                    labelResource = R.string.gesture_patterns_title,
                    drawableId = R.drawable.ic_gesture_patterns,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openGesturePatternsMenu() }
                )
            } else null,
            deviceItem,
            MenuItem(
                id = "media_control",
                labelResource = R.string.menu_title_media_control,
                drawableId = R.drawable.ic_media_control,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openMediaControlMenu() }
            ),
            if (NodeExaminer.canPerformEditActions(GesturePoint.getPoint())) {
                MenuItem(
                    id = "edit",
                    labelResource = R.string.menu_title_edit,
                    drawableId = R.drawable.ic_edit,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openEditMenu() }
                )
            } else null,
            if (AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.ITEM_SCAN) {
                MenuItem(
                    id = "switch_to_item_scan",
                    labelResource = R.string.access_technique_item_scan,
                    drawableId = R.drawable.ic_item_scan,
                    action = {
                        MenuManager.getInstance().switchToItemScan()
                    }
                )
            } else null,
            if (AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.RADAR &&
                !scanSettings.isDirectionalScanMode()
            ) {
                MenuItem(
                    id = "switch_to_radar",
                    labelResource = R.string.access_technique_radar,
                    drawableId = R.drawable.ic_radar,
                    action = {
                        MenuManager.getInstance().switchToRadar()
                    }
                )
            } else null,
            if (AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.POINT_SCAN &&
                !scanSettings.isDirectionalScanMode()
            ) {
                MenuItem(
                    id = "switch_to_point_scan",
                    labelResource = R.string.access_technique_point_scan,
                    drawableId = R.drawable.ic_point_scan,
                    action = {
                        MenuManager.getInstance().switchToPointScan()
                    }
                )
            } else null,
            MenuItem(
                id = "pause",
                labelResource = R.string.menu_item_pause,
                drawableId = R.drawable.ic_pause,
                action = {
                    ServiceCore.getPauseManager()?.startPause()
                }
            )
        )
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
