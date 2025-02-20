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
            textResource = R.string.menu_title_my_actions,
            isLinkToMenu = true,
            action = { MenuManager.getInstance().openMyActionsMenu() }
        )
    }

    val deviceItem = MenuItem(
        id = "device",
        textResource = R.string.menu_title_device,
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openDeviceMenu() }
    )

    val mainMenuObject = MenuStructure(
        id = "main_menu",
        items = listOfNotNull(
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
            if (ScanMethod.getType() != ScanMethod.MethodType.ITEM_SCAN && ScanMethod.getType() != ScanMethod.MethodType.RADAR) {
                MenuItem(
                    id = "refine_selection",
                    textResource = R.string.menu_item_refine_selection,
                    action = { GesturePoint.setReselect(true) }
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
            if (ScanMethod.getType() != ScanMethod.MethodType.ITEM_SCAN) {
                MenuItem(
                    id = "switch_to_item_scan",
                    textResource = R.string.menu_item_switch_to_item_scan,
                    action = {
                        MenuManager.getInstance().switchToItemScan()
                    }
                )
            } else null,
            if (ScanMethod.getType() != ScanMethod.MethodType.RADAR) {
                MenuItem(
                    id = "switch_to_radar",
                    textResource = R.string.menu_item_switch_to_radar,
                    action = {
                        MenuManager.getInstance().switchToRadar()
                    }
                )
            } else null,
            if (ScanMethod.getType() != ScanMethod.MethodType.CURSOR) {
                MenuItem(
                    id = "switch_to_cursor",
                    textResource = R.string.menu_item_switch_to_cursor,
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