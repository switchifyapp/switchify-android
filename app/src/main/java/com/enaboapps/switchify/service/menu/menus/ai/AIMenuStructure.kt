package com.enaboapps.switchify.service.menu.menus.ai

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.LoadingMenu
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.techniques.nodes.scanners.system.SystemNodeHolder

/**
 * AIMenuStructure provides the menu structure and navigation for AI-powered features
 */
object AIMenuStructure {

    /**
     * Creates the main AI menu item that can be added to other menus
     */
    fun createAIMenuItem(accessibilityService: SwitchifyAccessibilityService): MenuItem {
        return MenuItem(
            id = "ai_suggestions",
            textResource = R.string.menu_ai_suggestions,
            isLinkToMenu = true,
            closeOnSelect = false
        ) {
            // Show the AI menu
            showAIMenu(accessibilityService)
        }
    }

    /**
     * Shows the AI suggestions menu with loading state
     */
    private fun showAIMenu(accessibilityService: SwitchifyAccessibilityService) {
        // First show loading menu
        val loadingMenu = LoadingMenu(
            accessibilityService = accessibilityService,
            loadingTextResource = R.string.ai_menu_analyzing
        )
        val loadingMenuView = loadingMenu.build()
        MenuManager.getInstance().menuHierarchy?.openMenu(loadingMenuView)
        
        // Build AI menu asynchronously and wait for response
        CoroutineScope(Dispatchers.Main).launch {
            // Get the AI suggestions first
            val aiSuggestions = loadAISuggestions(accessibilityService)
            
            // Create AI menu with the loaded suggestions
            val aiMenu = BaseMenu(
                accessibilityService = accessibilityService,
                items = aiSuggestions,
                showSystemNavItems = true,
                showNavMenuItems = true
            )
            val menuView = aiMenu.build()
            
            // Replace loading menu with AI menu
            MenuManager.getInstance().replaceCurrentMenu(menuView)
        }
    }

    private const val TAG = "AIMenuStructure"
    private const val MAX_SUGGESTIONS = 30

    /**
     * Dynamically loads AI-powered node suggestions
     */
    private suspend fun loadAISuggestions(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
        Log.d(TAG, "Loading AI suggestions...")

        try {
            // Get current nodes from the node system
            val currentNodes = SystemNodeHolder.getNodes()
            
            if (currentNodes.isEmpty()) {
                Log.d(TAG, "No nodes available for AI analysis")
                return createEmptyStateMenuItem()
            }

            Log.d(TAG, "Analyzing ${currentNodes.size} nodes with AI...")

            // Rank nodes using AI
            val rankedNodes = AINodeRanker.rankNodes(currentNodes)
            
            if (rankedNodes.isEmpty()) {
                Log.d(TAG, "No ranked nodes returned from AI analysis")
                return createNoSuggestionsMenuItem()
            }

            Log.d(TAG, "Creating menu items for ${rankedNodes.size} ranked nodes")

            // Convert ranked nodes to menu items
            val menuItems = rankedNodes.take(MAX_SUGGESTIONS).mapIndexed { index, rankedNode ->
                createNodeMenuItem(
                    rankedNode = rankedNode,
                    index = index + 1
                )
            }

            return menuItems

        } catch (e: Exception) {
            Log.e(TAG, "Error loading AI suggestions", e)
            return createErrorMenuItem()
        }
    }

    /**
     * Creates a menu item for a ranked node
     */
    private fun createNodeMenuItem(
        rankedNode: AINodeRanker.RankedNode,
        index: Int
    ): MenuItem {
        val node = rankedNode.node
        val contentDescription = node.getContentDescription()
        
        // Create a concise display text
        val displayText = if (contentDescription.length > 30) {
            contentDescription.take(27) + "..."
        } else {
            contentDescription
        }

        // Add priority indicator based on dynamic scoring
        val priorityText = when {
            rankedNode.score >= 80 -> "★★ $displayText"  // Very high priority
            rankedNode.score >= 60 -> "★ $displayText"   // High priority
            rankedNode.score >= 30 -> "• $displayText"   // Medium priority
            else -> displayText                           // Lower priority
        }

        return MenuItem(
            id = "ai_suggestion_$index",
            userProvidedText = priorityText,
            closeOnSelect = true
        ) {
            // When selected, tap at the node's location
            tapAtNode(node)
        }
    }

    /**
     * Performs tap action at the node's location
     */
    private fun tapAtNode(node: com.enaboapps.switchify.service.techniques.nodes.Node) {
        Log.d(TAG, "Tapping at node: ${node.getContentDescription()}")
        
        try {
            // Directly tap at the node's center using GestureManager
            GestureManager.instance.performTap(node.getMidX(), node.getMidY())
            
            Log.d(TAG, "Tap performed at (${node.getMidX()}, ${node.getMidY()})")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing tap at node location", e)
        }
    }

    /**
     * Creates menu item for empty state (no nodes available)
     */
    private fun createEmptyStateMenuItem(): List<MenuItem> {
        return listOf(
            MenuItem(
                id = "ai_empty_state",
                textResource = R.string.ai_menu_no_elements,
                closeOnSelect = false
            ) {
                // No action needed for info item
            }
        )
    }

    /**
     * Creates menu item when no suggestions are available
     */
    private fun createNoSuggestionsMenuItem(): List<MenuItem> {
        return listOf(
            MenuItem(
                id = "ai_no_suggestions",
                textResource = R.string.ai_menu_no_suggestions,
                closeOnSelect = false
            ) {
                // No action needed for info item
            }
        )
    }

    /**
     * Creates menu item for error state
     */
    private fun createErrorMenuItem(): List<MenuItem> {
        return listOf(
            MenuItem(
                id = "ai_error",
                textResource = R.string.ai_menu_error,
                closeOnSelect = false
            ) {
                // No action needed for error info item
            }
        )
    }
}