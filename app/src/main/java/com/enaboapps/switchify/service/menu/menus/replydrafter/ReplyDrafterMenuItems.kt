package com.enaboapps.switchify.service.menu.menus.replydrafter

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.llm.ReplyDrafterTextInserter
import com.enaboapps.switchify.service.menu.MenuItem

object ReplyDrafterMenuItems {

    /**
     * Build one menu item per reply suggestion. Selecting an item inserts that
     * reply into the focused text field. An empty list yields a single
     * non-actionable placeholder so the menu is never empty.
     */
    fun build(
        service: SwitchifyAccessibilityService,
        suggestions: List<String>
    ): List<MenuItem> {
        if (suggestions.isEmpty()) {
            return listOf(
                MenuItem(
                    id = "no_reply_suggestions",
                    labelResource = R.string.reply_drafter_no_suggestions,
                    descriptionResource = R.string.menu_item_reply_suggestion_description,
                    closeOnSelect = false,
                    action = { }
                )
            )
        }
        return suggestions.mapIndexed { index, suggestion ->
            MenuItem(
                id = "reply_suggestion_$index",
                userProvidedText = suggestion,
                descriptionResource = R.string.menu_item_reply_suggestion_description,
                closeOnSelect = true,
                action = { ReplyDrafterTextInserter.insert(service, suggestion) }
            )
        }
    }
}
