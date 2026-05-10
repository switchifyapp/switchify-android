package com.enaboapps.switchify.service.menu

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.BaseMenu

/**
 * LoadingMenu provides a reusable loading state display for any menu operation
 */
class LoadingMenu(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val loadingTextResource: Int,
    showSystemNavItems: Boolean = false,
    showNavMenuItems: Boolean = false
) {

    private val baseMenu: BaseMenu

    init {
        // Create loading menu item
        val loadingItem = MenuItem(
            id = "loading",
            labelResource = loadingTextResource,
            descriptionResource = R.string.menu_item_loading_description,
            closeOnSelect = false
        ) {
            // No action for loading state
        }

        baseMenu = BaseMenu(
            accessibilityService = accessibilityService,
            items = listOf(loadingItem),
            showNavMenuItems = showNavMenuItems
        )
    }

    fun build(): MenuView {
        return baseMenu.build()
    }
}