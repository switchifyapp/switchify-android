package com.enaboapps.switchify.service.menu.menus.pc

import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants

/**
 * Menu shown when more than one paired Switchify PC is discovered on the local
 * network, letting the user pick which PC to connect to. Selecting a row
 * closes the menu hierarchy and invokes [onSelect] with the chosen PC.
 */
class ChoosePcMenu(
    accessibilityService: SwitchifyAccessibilityService,
    pcs: List<DiscoveredPc>,
    onSelect: (DiscoveredPc) -> Unit
) : BaseMenu(
    accessibilityService = accessibilityService,
    items = ChoosePcMenuStructure().getMenuItems(pcs, onSelect),
    menuId = MenuConstants.MenuIds.CHOOSE_PC_MENU,
    showNavMenuItems = true
)
