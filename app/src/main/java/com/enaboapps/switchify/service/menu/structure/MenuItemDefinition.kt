package com.enaboapps.switchify.service.menu.structure

/**
 * Lightweight metadata for a menu item.
 * This separates WHAT menu items exist (metadata) from HOW they behave (actions).
 * Used as a single source of truth for menu item definitions.
 *
 * @property id Unique identifier for the menu item
 * @property labelResource String resource ID for the label
 * @property userProvidedText User-provided text (alternative to labelResource)
 * @property descriptionResource String resource ID for the one-line description
 *   shown below the name in the highlight header during scanning. Required
 *   alongside [userProvidedDescription]: every item must surface a description
 *   so the scanning user gets plain-language confirmation of the action.
 * @property userProvidedDescription Runtime description for items whose copy is
 *   not in resources (e.g., per-app entries in the favourite apps menu).
 *   Falls back to [descriptionResource] when null.
 * @property drawableId Drawable resource ID for the icon
 * @property circleText Optional short text rendered inside the menu circle in
 *   place of an icon. When set, this overrides both [drawableId] and the
 *   automatic initials fallback. The full [labelResource] is still used for
 *   accessibility and the highlight header.
 * @property isMenuHierarchyManipulator Whether this manipulates the menu hierarchy
 */
data class MenuItemDefinition(
    val id: String,
    val labelResource: Int? = null,
    val userProvidedText: String? = null,
    val descriptionResource: Int?,
    val userProvidedDescription: String? = null,
    val drawableId: Int = 0,
    val circleText: String? = null,
    val isMenuHierarchyManipulator: Boolean = false
)
