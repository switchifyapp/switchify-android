package com.enaboapps.switchify.service.menu.menus.custom

import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.custom.actions.ActionPerformer
import com.enaboapps.switchify.service.custom.actions.store.ActionStore
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class CustomActionsMenuStructure(private val accessibilityService: SwitchifyAccessibilityService?) {
    fun buildMyActionsMenuObject(): MenuStructure {
        if (accessibilityService == null) {
            return MenuStructure(id = "my_actions_menu", items = emptyList())
        }
        val actionStore = ActionStore(accessibilityService)
        val actions = actionStore.getActions()
        return MenuStructure(
            id = "my_actions_menu",
            items = actions.map { action ->
                MenuItem(
                    id = action.id,
                    text = action.text,
                    action = {
                        val actionPerformer = ActionPerformer(accessibilityService)
                        actionPerformer.performActionFromStore(action.id)
                    }
                )
            }
        )
    }
} 