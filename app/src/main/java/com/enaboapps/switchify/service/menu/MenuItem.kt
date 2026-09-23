package com.enaboapps.switchify.service.menu

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemDefinition
import com.enaboapps.switchify.utils.Resources

internal enum class MenuItemVisualRole {
    REGULAR,
    BACK,
    NAVIGATION,
    CLOSE;

    companion object {
        fun resolve(
            id: String,
            isBackButton: Boolean,
            isMenuHierarchyManipulator: Boolean
        ): MenuItemVisualRole = when {
            id == MenuConstants.ItemIds.Navigation.CLOSE_MENU -> CLOSE
            isBackButton -> BACK
            isMenuHierarchyManipulator -> NAVIGATION
            else -> REGULAR
        }
    }
}

internal enum class MenuSelectionSource {
    TOUCH,
    SCANNING
}

/**
 * This class represents a menu item
 * @property id The id of the menu item
 * @property labelResource The resource id of the label text (used for both display and accessibility)
 * @property userProvidedText The text of the menu item if it is user-provided
 * @property descriptionResource Resource id of the one-line description shown
 *   below the name in the highlight header during scanning. Every item must
 *   surface a description so scanning users get plain-language confirmation of
 *   the action; either this or [userProvidedDescription] must be supplied.
 * @property userProvidedDescription Runtime description for items whose copy
 *   is not in resources (e.g., per-app entries in the favourite apps menu).
 * @property drawableId The drawable resource id of the menu item
 * @property circleText Optional short text rendered inside the menu circle in
 *   place of an icon. When set, this overrides both the icon and the
 *   automatic initials fallback. The full label still drives accessibility.
 * @property closeOnSelect Whether the menu should close when the item is selected
 * @property isLinkToMenu Whether the item is a link to another menu
 * @property isMenuHierarchyManipulator Whether the item manipulates the menu hierarchy
 * @property action The action to perform when the item is selected
 */
class MenuItem(
    val id: String,
    val labelResource: Int? = null,
    val userProvidedText: String? = null,
    val descriptionResource: Int?,
    val userProvidedDescription: String? = null,
    private val drawableId: Int = 0,
    private val circleText: String? = null,
    val closeOnSelect: Boolean = true,
    private val requiresScanningSelection: Boolean = false,
    private val onRejectedTouchSelection: (() -> Unit)? = null,
    var isLinkToMenu: Boolean = false,
    var isMenuHierarchyManipulator: Boolean = false,
    val isBackButton: Boolean = false,
    private val action: () -> Unit
) {
    internal fun displayText(): String =
        resolveMenuItemLabel(labelResource, userProvidedText).orEmpty()

    /**
     * Convenience constructor that accepts a MenuItemDefinition.
     * This ensures menu metadata is defined once in MenuItemRegistry.
     */
    constructor(
        definition: MenuItemDefinition,
        closeOnSelect: Boolean = true,
        isLinkToMenu: Boolean = false,
        action: () -> Unit
    ) : this(
        id = definition.id,
        labelResource = definition.labelResource,
        userProvidedText = definition.userProvidedText,
        descriptionResource = definition.descriptionResource,
        userProvidedDescription = definition.userProvidedDescription,
        drawableId = definition.drawableId,
        circleText = definition.circleText,
        closeOnSelect = closeOnSelect,
        isLinkToMenu = isLinkToMenu,
        isMenuHierarchyManipulator = definition.isMenuHierarchyManipulator,
        action = action
    )

    private var composeView: AccessibilityComposeView? = null

    internal fun inflateGrid(
        parent: ViewGroup,
        menuSize: MenuItemSize,
        widthPx: Int,
        heightPx: Int,
        navigation: Boolean = false,
        isTransparent: Boolean = false
    ) {
        composeView = AccessibilityComposeView(parent.context) {
            MenuGridTile(
                text = labelResource?.let { stringResource(it) } ?: userProvidedText.orEmpty(),
                drawableId = drawableId,
                circleText = circleText,
                visualRole = MenuItemVisualRole.resolve(id, isBackButton, isMenuHierarchyManipulator),
                menuSize = menuSize,
                navigation = navigation,
                isTransparent = isTransparent,
                onClick = { select() }
            )
        }.also { view ->
            view.layoutParams = ViewGroup.LayoutParams(widthPx, heightPx)
            parent.addView(view)
        }
    }

    internal fun releaseView() {
        composeView = null
    }

    /**
     * Select the menu item
     */
    fun select() {
        select(MenuSelectionSource.TOUCH)
    }

    internal fun select(source: MenuSelectionSource) {
        if (requiresScanningSelection && source != MenuSelectionSource.SCANNING) {
            onRejectedTouchSelection?.invoke()
            return
        }
        if (!isLinkToMenu && !isMenuHierarchyManipulator && closeOnSelect) {
            MenuManager.getInstance().closeMenuHierarchy()
        }
        action()
    }

    /**
     * Get the location of the menu item on the screen
     * @return The location of the menu item on the screen
     */
    private fun getLocationOnScreen(): IntArray {
        val location = IntArray(2)
        composeView?.getLocationOnScreen(location)
        return location
    }

    /**
     * Get the x coordinate of the menu item
     * @return The x coordinate of the menu item
     */
    val x: Int
        get() = getLocationOnScreen()[0]

    /**
     * Get the y coordinate of the menu item
     * @return The y coordinate of the menu item
     */
    val y: Int
        get() = getLocationOnScreen()[1]

    /**
     * Get the width of the menu item
     * @return The width of the menu item
     */
    val width: Int
        get() = composeView?.width ?: 0

    /**
     * Get the height of the menu item
     * @return The height of the menu item
     */
    val height: Int
        get() = composeView?.height ?: 0
}

@Composable
private fun MenuGridTile(
    text: String,
    drawableId: Int,
    circleText: String?,
    visualRole: MenuItemVisualRole,
    menuSize: MenuItemSize,
    navigation: Boolean,
    isTransparent: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseColor = when (visualRole) {
        MenuItemVisualRole.CLOSE -> MaterialTheme.colorScheme.errorContainer
        MenuItemVisualRole.BACK -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }.copy(alpha = if (isTransparent) 0.84f else 1f)
    val color = if (isPressed) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f).compositeOver(baseColor)
    } else baseColor
    val foreground = if (visualRole == MenuItemVisualRole.CLOSE) {
        MaterialTheme.colorScheme.onErrorContainer
    } else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = text
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        if (circleText != null) {
            Text(text = circleText, color = foreground, fontSize = menuSize.labelTextSize,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else if (drawableId != 0) {
            Icon(
                painter = painterResource(drawableId),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(if (navigation) 24.dp else menuSize.iconSize)
            )
        }
        Text(
            text = text,
            color = foreground,
            fontSize = menuSize.labelTextSize,
            lineHeight = menuSize.labelTextSize * 1.3f,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = if (navigation) 2 else 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun resolveMenuItemLabel(labelResource: Int?, userProvidedText: String?): String? =
    if (labelResource != null) Resources.getString(labelResource) else userProvidedText
