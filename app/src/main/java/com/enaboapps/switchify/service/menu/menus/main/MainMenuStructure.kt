package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.custom.actions.store.ActionStore
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.methods.nodes.NodeExaminer
import com.enaboapps.switchify.service.scanning.ScanMethod
import com.enaboapps.switchify.service.utils.DeviceLockObserver

class MainMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {
    private val gestureMenuStructure = GestureMenuStructure()
    private val deviceLockObserver = accessibilityService?.let { DeviceLockObserver(it) }

    private fun createMyActionsMenuItem(): MenuItem? {
        if (deviceLockObserver?.isUserUnlocked() != true || accessibilityService == null) {
            return null
        }

        val actionStore = ActionStore(accessibilityService)
        if (actionStore.isEmpty()) {
            return null
        }

        return MenuItem(
            id = "my_actions",
            text = "My Actions",
            isLinkToMenu = true,
            action = { MenuManager.getInstance().openMyActionsMenu() }
        )
    }

    val deviceItem = MenuItem(
        id = "device",
        text = "Device",
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openDeviceMenu() }
    )

    val mainMenuObject = MenuStructure(
        id = "main_menu",
        items = listOfNotNull(
            gestureMenuStructure.tapMenuItem,
            MenuItem(
                id = "gestures",
                text = "Gestures",
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openGesturesMenu() }
            ),
            MenuItem(
                id = "scroll",
                text = "Scroll",
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openScrollMenu() }
            ),
            if (ScanMethod.getType() != ScanMethod.MethodType.ITEM_SCAN && ScanMethod.getType() != ScanMethod.MethodType.RADAR) {
                MenuItem(
                    id = "refine_selection",
                    text = "Refine Selection",
                    action = { GesturePoint.setReselect(true) }
                )
            } else null,
            deviceItem,
            MenuItem(
                id = "media_control",
                text = "Media Control",
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openMediaControlMenu() }
            ),
            if (NodeExaminer.canPerformEditActions(GesturePoint.getPoint())) {
                MenuItem(
                    id = "edit",
                    text = "Edit",
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openEditMenu() }
                )
            } else null,
            if (ScanMethod.getType() != ScanMethod.MethodType.ITEM_SCAN) {
                MenuItem(
                    id = "switch_to_item_scan",
                    text = ScanMethod.getName(ScanMethod.MethodType.ITEM_SCAN),
                    action = {
                        MenuManager.getInstance().switchToItemScan()
                    }
                )
            } else null,
            if (ScanMethod.getType() != ScanMethod.MethodType.RADAR) {
                MenuItem(
                    id = "switch_to_radar",
                    text = ScanMethod.getName(ScanMethod.MethodType.RADAR),
                    action = {
                        MenuManager.getInstance().switchToRadar()
                    }
                )
            } else null,
            if (ScanMethod.getType() != ScanMethod.MethodType.CURSOR) {
                MenuItem(
                    id = "switch_to_cursor",
                    text = ScanMethod.getName(ScanMethod.MethodType.CURSOR),
                    action = {
                        MenuManager.getInstance().switchToCursor()
                    }
                )
            } else null,
            createMyActionsMenuItem()
        )
    )

    val menuManipulatorItems = listOfNotNull(
        if (MenuManager.getInstance().menuHierarchy?.getTopMenu() != null) {
            MenuItem(
                id = "previous_menu",
                drawableId = R.drawable.ic_previous_menu,
                drawableDescription = "Previous menu",
                showDrawableDescription = false,
                isSmall = true,
                isMenuHierarchyManipulator = true,
                action = { MenuManager.getInstance().menuHierarchy?.popMenu() }
            )
        } else null,
        MenuItem(
            id = "close_menu",
            drawableId = R.drawable.ic_close_menu,
            drawableDescription = "Close menu",
            showDrawableDescription = false,
            isSmall = true,
            isMenuHierarchyManipulator = true,
            action = { MenuManager.getInstance().closeMenuHierarchy() }
        )
    )
} 