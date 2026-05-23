package com.enaboapps.switchify.service.menu.menus.ai

import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.llm.ReplyDrafterManager
import com.enaboapps.switchify.service.llm.ScreenHighlightsManager
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import kotlinx.coroutines.CoroutineScope

class AiMenuStructure(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val coroutineScope: CoroutineScope
) {

    /**
     * Build the AI submenu containing the on-device AI features (Reply Drafter
     * and Screen Highlights). Each item keeps the per-action Pro gate it had
     * on the main menu — the submenu link itself opens freely so users can
     * discover the features.
     */
    fun buildAiMenuObject(): MenuStructure {
        return MenuStructure(
            id = MenuConstants.MenuIds.AI_MENU,
            items = listOfNotNull(
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.AI_MENU, MenuConstants.ItemIds.Ai.REPLY_DRAFTER)?.let { def ->
                    MenuItem(
                        definition = def,
                        action = {
                            IAPHandler.runIfProPurchased(accessibilityService) {
                                ReplyDrafterManager.startDrafting(accessibilityService)
                            }
                        }
                    )
                },
                MenuItemRegistry.getDefinition(MenuConstants.MenuIds.AI_MENU, MenuConstants.ItemIds.Ai.SCREEN_HIGHLIGHTS)?.let { def ->
                    MenuItem(
                        definition = def,
                        action = {
                            IAPHandler.runIfProPurchased(accessibilityService) {
                                ScreenHighlightsManager.startExtracting(accessibilityService)
                            }
                        }
                    )
                }
            ),
            context = accessibilityService,
            coroutineScope = coroutineScope
        )
    }
}
