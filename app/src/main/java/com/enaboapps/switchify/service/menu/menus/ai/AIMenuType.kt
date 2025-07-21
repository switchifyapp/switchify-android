package com.enaboapps.switchify.service.menu.menus.ai

/**
 * Enum defining different types of AI-powered menus for future extensibility
 */
enum class AIMenuType(
    val displayName: String,
    val description: String
) {
    /**
     * General suggestions based on current screen analysis
     */
    SMART_SUGGESTIONS(
        displayName = "Smart Suggestions",
        description = "AI-ranked most important elements on current screen"
    ),

    /**
     * Context-aware suggestions based on app type and user patterns
     */
    CONTEXTUAL_ACTIONS(
        displayName = "Contextual Actions", 
        description = "Actions relevant to current app and context"
    ),

    /**
     * Quick access to frequently used elements
     */
    QUICK_ACCESS(
        displayName = "Quick Access",
        description = "AI-identified frequently accessed elements"
    ),

    /**
     * Task-oriented suggestions for completing common workflows
     */
    TASK_FOCUSED(
        displayName = "Task Assistant",
        description = "AI suggestions for completing common tasks"
    );

    companion object {
        /**
         * Gets the default AI menu type
         */
        fun getDefault(): AIMenuType = SMART_SUGGESTIONS

        /**
         * Gets all available AI menu types
         */
        fun getAllTypes(): List<AIMenuType> = values().toList()

        /**
         * Finds AI menu type by display name
         */
        fun fromDisplayName(name: String): AIMenuType? {
            return values().find { it.displayName.equals(name, ignoreCase = true) }
        }
    }
}