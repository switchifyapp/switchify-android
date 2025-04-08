package com.enaboapps.switchify.service.menu.menus.main

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.actions.custom.store.ActionStore
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.NodeExaminer
import com.enaboapps.switchify.service.utils.DeviceLockObserver

class MainMenuStructure(private val accessibilityService: SwitchifyAccessibilityService) {
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService)
    private val deviceLockObserver = DeviceLockObserver(accessibilityService)

    private fun createMyActionsMenuItem(): MenuItem? {
        if (deviceLockObserver.isUserUnlocked() != true) {
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
            createMyActionsMenuItem(),
            MenuItem(
                id = "pause",
                textResource = R.string.menu_item_pause,
                action = {
                    ServiceCore.getExternalSwitchListener()?.startPauseJob()
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