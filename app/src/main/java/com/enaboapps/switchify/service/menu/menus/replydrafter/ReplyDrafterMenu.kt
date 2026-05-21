package com.enaboapps.switchify.service.menu.menus.replydrafter

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuLayoutType
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants

/**
 * Linear, switch-scannable menu of AI-generated reply suggestions.
 */
class ReplyDrafterMenu(
    accessibilityService: SwitchifyAccessibilityService,
    suggestions: List<String>
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = ReplyDrafterMenuItems.build(accessibilityService, suggestions),
    menuId = MenuConstants.MenuIds.REPLY_DRAFTER_MENU,
    showNavMenuItems = true,
    layoutType = MenuLayoutType.LINEAR
)
