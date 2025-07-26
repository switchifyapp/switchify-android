package com.enaboapps.switchify.service.menu.menus.scroll

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class ScrollMenuStructure(accessibilityService: SwitchifyAccessibilityService) {
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService)

    val scrollMenuObject = MenuStructure(
        id = "scroll_menu",
        items = listOfNotNull(
            MenuItem(
                id = "scroll_up",
                textResource = R.string.menu_item_scroll_up,
                drawableId = R.drawable.ic_scroll_up,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_UP)
                }
            ),
            MenuItem(
                id = "scroll_down",
                textResource = R.string.menu_item_scroll_down,
                drawableId = R.drawable.ic_scroll_down,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_DOWN)
                }
            ),
            MenuItem(
                id = "scroll_left",
                textResource = R.string.menu_item_scroll_left,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_LEFT)
                }
            ),
            MenuItem(
                id = "scroll_right",
                textResource = R.string.menu_item_scroll_right,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_RIGHT)
                }
            ),
            gestureMenuStructure.toggleGestureLockMenuItem
        )
    )
} 