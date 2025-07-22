package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.menus.ai.AIMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.DeviceLockObserver
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.backend.iap.IAPHandler

class MainMenuStructure(private val accessibilityService: SwitchifyAccessibilityService) {
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService)
    private val deviceLockObserver = DeviceLockObserver(accessibilityService)
    private val preferenceManager = PreferenceManager(accessibilityService)

    val deviceItem = MenuItem(
        id = "device",
        textResource = R.string.menu_title_device,
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openDeviceMenu() }
    )

    val mainMenuObject = MenuStructure(
        id = "main_menu",
        items = listOfNotNull(
            // Show "Scan Keyboard" menu item when keyboard is visible but user has escaped
            if (KeyboardManager.shouldShowScanKeyboardMenuItem()) {
                MenuItem(
                    id = "scan_keyboard",
                    textResource = R.string.menu_item_scan_keyboard,
                    action = {
                        KeyboardManager.returnToKeyboard()
                        MenuManager.getInstance().closeMenuHierarchy()
                    }
                )
            } else null,
            gestureMenuStructure.tapMenuItem,
            MenuItem(
                id = "gestures",
                textResource = R.string.menu_title_gestures,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openGesturesMenu() }
            ),
            MenuItem(
                id = "scroll",
                textResource = R.string.menu_title_scroll,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openScrollMenu() }
            ),
            // AI-powered node suggestions (Pro feature)
            if (preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_AI_SUGGESTIONS_ENABLED) && 
                IAPHandler.hasPurchasedPro()) {
                AIMenuStructure.createAIMenuItem(accessibilityService)
            } else null,
            if (deviceLockObserver.isUserUnlocked() == true) {
                MenuItem(
                    id = "quick_apps",
                    textResource = R.string.menu_title_quick_apps,
                    isLinkToMenu = true,
                    action = {
                        MenuManager.getInstance().openQuickAppsMenu()
                    }
                )
            } else null,
            if (deviceLockObserver.isUserUnlocked() == true) {
                MenuItem(
                    id = "gesture_patterns",
                    textResource = R.string.gesture_patterns_title,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openGesturePatternsMenu() }
                )
            } else null,
            deviceItem,
            MenuItem(
                id = "media_control",
                textResource = R.string.menu_title_media_control,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openMediaControlMenu() }
            ),
            if (NodeExaminer.canPerformEditActions(GesturePoint.getPoint())) {
                MenuItem(
                    id = "edit",
                    textResource = R.string.menu_title_edit,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openEditMenu() }
                )
            } else null,
            if (AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.ITEM_SCAN) {
                MenuItem(
                    id = "switch_to_item_scan",
                    textResource = R.string.access_technique_item_scan,
                    action = {
                        MenuManager.getInstance().switchToItemScan()
                    }
                )
            } else null,
            if (AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.RADAR) {
                MenuItem(
                    id = "switch_to_radar",
                    textResource = R.string.access_technique_radar,
                    action = {
                        MenuManager.getInstance().switchToRadar()
                    }
                )
            } else null,
            if (AccessTechnique.getCurrentTechnique() != AccessTechnique.Technique.CURSOR) {
                MenuItem(
                    id = "switch_to_cursor",
                    textResource = R.string.access_technique_cursor,
                    action = {
                        MenuManager.getInstance().switchToCursor()
                    }
                )
            } else null,
            MenuItem(
                id = "pause",
                textResource = R.string.menu_item_pause,
                action = {
                    ServiceCore.getPauseManager()?.startPause()
                }
            )
        )
    )

    val menuManipulatorItems = listOfNotNull(
        if (MenuManager.getInstance().menuHierarchy?.isAtFirstMenu() == false) {
            MenuItem(
                id = "previous_menu",
                drawableId = R.drawable.ic_previous_menu,
                drawableDescriptionResource = R.string.menu_item_previous_menu,
                showDrawableDescription = false,
                isSmall = true,
                isMenuHierarchyManipulator = true,
                action = { MenuManager.getInstance().menuHierarchy?.popMenu() }
            )
        } else null,
        MenuItem(
            id = "close_menu",
            drawableId = R.drawable.ic_close_menu,
            drawableDescriptionResource = R.string.menu_item_close_menu,
            showDrawableDescription = false,
            isSmall = true,
            isMenuHierarchyManipulator = true,
            action = { MenuManager.getInstance().closeMenuHierarchy() }
        )
    )
} 