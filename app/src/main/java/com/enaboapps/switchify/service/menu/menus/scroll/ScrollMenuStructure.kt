package com.enaboapps.switchify.service.menu.menus.scroll

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import kotlinx.coroutines.CoroutineScope

class ScrollMenuStructure(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val coroutineScope: CoroutineScope
) {
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService, coroutineScope)

    val scrollMenuObject = MenuStructure(
        id = MenuConstants.MenuIds.SCROLL_MENU,
        items = listOfNotNull(
            MenuItemRegistry.getDefinition(MenuConstants.MenuIds.SCROLL_MENU, MenuConstants.ItemIds.Scroll.SCROLL_UP)?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_UP) }
                )
            },
            MenuItemRegistry.getDefinition(MenuConstants.MenuIds.SCROLL_MENU, MenuConstants.ItemIds.Scroll.SCROLL_DOWN)?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_DOWN) }
                )
            },
            MenuItemRegistry.getDefinition(MenuConstants.MenuIds.SCROLL_MENU, MenuConstants.ItemIds.Scroll.SCROLL_LEFT)?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_LEFT) }
                )
            },
            MenuItemRegistry.getDefinition(MenuConstants.MenuIds.SCROLL_MENU, MenuConstants.ItemIds.Scroll.SCROLL_RIGHT)?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_RIGHT) }
                )
            },
            gestureMenuStructure.toggleGestureLockMenuItem
        ),
        context = accessibilityService,
        coroutineScope = coroutineScope
    )
} 