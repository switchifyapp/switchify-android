package com.enaboapps.switchify.service.menu.menus.pc

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.service.menu.MenuItem

/**
 * Builds menu items for the "Choose PC" menu, one per paired discovered PC.
 */
class ChoosePcMenuStructure {

    fun getMenuItems(
        pcs: List<DiscoveredPc>,
        onSelect: (DiscoveredPc) -> Unit
    ): List<MenuItem> {
        return pcs.map { pc ->
            MenuItem(
                id = "choose_pc_${pc.desktopId}",
                userProvidedText = pickerLabel(pc),
                descriptionResource = R.string.menu_item_choose_pc_description,
                drawableId = R.drawable.ic_control_pc,
                action = { onSelect(pc) }
            )
        }
    }

    private fun pickerLabel(pc: DiscoveredPc): String {
        return pc.bluetoothEndpoint?.deviceName?.trim()?.takeIf { it.isNotBlank() }
            ?: pc.displayName
    }
}
