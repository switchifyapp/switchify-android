package com.enaboapps.switchify.service.menu

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.menu.structure.MenuItemDefinition
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.utils.Resources

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
    var isLinkToMenu: Boolean = false,
    var isMenuHierarchyManipulator: Boolean = false,
    val isBackButton: Boolean = false,
    private val action: () -> Unit
) {
    internal fun displayText(): String =
        resolveMenuItemLabel(labelResource, userProvidedText).orEmpty()

    internal val showsForwardChevron: Boolean
        get() = isLinkToMenu && !isBackButton

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

    /**
     * Inflate the menu item into [parent] using [menuSize] for its dimensions.
     *
     * Nav-row items ([isMenuHierarchyManipulator] = true) keep their fixed
     * cell size from the profile. Content items fill the parent row width
     * and use the profile's [MenuItemSize.rowHeightDp] for their height.
     */
    fun inflate(parent: ViewGroup, menuSize: MenuItemSize, navigationWidthPx: Int? = null) {
        val context = parent.context
        composeView = AccessibilityComposeView(context) {
            MenuItemContent(
                labelResource = labelResource,
                userProvidedText = userProvidedText,
                drawableId = drawableId,
                circleText = circleText,
                isMenuHierarchyManipulator = isMenuHierarchyManipulator,
                isLinkToMenu = isLinkToMenu,
                isBackButton = isBackButton,
                menuSize = menuSize,
                onClick = { select() }
            )
        }

        composeView?.let { view ->
            view.layoutParams = if (isMenuHierarchyManipulator) {
                val widthPx = navigationWidthPx
                    ?: ScreenUtils.dpToPx(context, menuSize.width.value.toInt())
                val heightPx = ScreenUtils.dpToPx(context, menuSize.height.value.toInt())
                ViewGroup.LayoutParams(widthPx, heightPx)
            } else {
                val rowHeightPx = ScreenUtils.dpToPx(context, menuSize.rowHeightDp)
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    rowHeightPx
                )
            }
            parent.addView(view)
        }
    }

    /**
     * Select the menu item
     */
    fun select() {
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
private fun MenuItemContent(
    labelResource: Int?,
    userProvidedText: String?,
    drawableId: Int,
    circleText: String?,
    isMenuHierarchyManipulator: Boolean,
    isLinkToMenu: Boolean,
    isBackButton: Boolean,
    menuSize: MenuItemSize,
    onClick: () -> Unit
) {
    val text = resolveMenuItemLabel(labelResource, userProvidedText)

    Box(modifier = Modifier.fillMaxSize()) {
        if (isMenuHierarchyManipulator) {
            NavigationMenuItem(
                drawableId = drawableId,
                labelResource = labelResource,
                menuSize = menuSize,
                onClick = onClick
            )
        } else {
            RegularMenuItem(
                text = text,
                drawableId = drawableId,
                circleText = circleText,
                labelResource = labelResource,
                menuSize = menuSize,
                isLinkToMenu = isLinkToMenu,
                isBackButton = isBackButton,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun NavigationMenuItem(
    drawableId: Int,
    labelResource: Int?,
    menuSize: MenuItemSize,
    onClick: () -> Unit
) {
    // Maintain full menu item size with centered circular icon
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(menuSize.padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Navigation button with circular background
        Box(
            modifier = Modifier
                .size(menuSize.navigationCircleSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = drawableId),
                contentDescription = labelResource?.let { Resources.getString(it) },
                modifier = Modifier.size(menuSize.navigationIconSize),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) // Dimmed
            )
        }
    }
}

@Composable
private fun RegularMenuItem(
    text: String?,
    drawableId: Int,
    circleText: String?,
    labelResource: Int?,
    menuSize: MenuItemSize,
    isLinkToMenu: Boolean,
    isBackButton: Boolean,
    onClick: () -> Unit
) {
    // List row: coloured circle on the left, full label text on the right,
    // sitting on a rounded surfaceVariant background so each row reads as a
    // tappable tile. Outer vertical padding creates a visible gap between
    // adjacent row backgrounds; the clip ensures the ripple respects the
    // rounded shape.
    //
    // Link-to-submenu items get a trailing chevron at the row's right edge.
    // The back-button item carries `isLinkToMenu = true` too (it links to
    // the previous menu), but a forward-pointing chevron next to its
    // back-arrow icon would point the wrong way — its secondaryContainer
    // circle and back-arrow icon already communicate the action.
    val circleColor = if (isBackButton) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.primaryContainer
    val iconTint = if (isBackButton) MaterialTheme.colorScheme.onSecondaryContainer
                   else MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(menuSize.containerCircleSize)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            // `circleText` overrides the icon: callers set it on items whose
            // value is best read as text inside the circle (e.g. the
            // tap-and-hold durations "0.5s", "1s", "2s"…). We size it
            // proportionally to its length so it fits the circle on every
            // size profile.
            if (circleText != null) {
                val fontScale = LocalConfiguration.current.fontScale.coerceAtLeast(0.5f)
                val effectiveFontSize = computeCircleTextFontSize(
                    text = circleText,
                    circleSizeDp = menuSize.containerCircleSize.value,
                    fontScale = fontScale,
                    fallback = menuSize.primaryTextSize
                )
                Text(
                    text = circleText,
                    color = iconTint,
                    fontSize = effectiveFontSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            } else if (drawableId != 0) {
                Icon(
                    painter = painterResource(id = drawableId),
                    contentDescription = labelResource?.let { Resources.getString(it) },
                    modifier = Modifier.size(menuSize.iconSize),
                    tint = iconTint
                )
            } else if (text != null) {
                // Cap the in-circle letter so the user's system font scale
                // (Settings → Display → Font size) does not push it past the
                // circle's edge when combined with Menu Size > 100 %. Clamp
                // the Sp value so the glyph stays within ~45 % of the
                // circle's diameter.
                val fontScale = LocalConfiguration.current.fontScale.coerceAtLeast(0.5f)
                val maxLetterSp = menuSize.containerCircleSize.value * 0.45f / fontScale
                val effectiveFontSize = if (menuSize.primaryTextSize.value > maxLetterSp) {
                    maxLetterSp.sp
                } else {
                    menuSize.primaryTextSize
                }
                Text(
                    text = circleInitials(text),
                    color = iconTint,
                    fontSize = effectiveFontSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = menuSize.labelTextSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isLinkToMenu && !isBackButton) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Pick a font size that lets [text] fit inside a circle of [circleSizeDp]
 * without overflowing, regardless of the user's system font scale or the
 * Menu Size setting.
 *
 * The character cap is ~45 % of the circle diameter for 1-char strings (matches
 * the legacy initials path) and shrinks proportionally for longer strings so
 * 3-4 character durations like "0.5s" and "10s" still fit. We never grow the
 * font past [fallback] (the profile's primaryTextSize); we only clamp it down.
 */
private fun computeCircleTextFontSize(
    text: String,
    circleSizeDp: Float,
    fontScale: Float,
    fallback: TextUnit
): TextUnit {
    val length = text.length.coerceAtLeast(1)
    // 0.45 fits one capital letter; for longer strings, allocate ~85 % of the
    // diameter across the characters and divide.
    val ratio = if (length <= 1) 0.45f else (0.85f / length).coerceAtMost(0.45f)
    val maxSp = circleSizeDp * ratio / fontScale
    return if (fallback.value > maxSp) maxSp.sp else fallback
}

/**
 * Single source of truth for a menu item's visible label — used both by the
 * rendered row and by the width measurement in MenuPage, so the two can
 * never resolve different text.
 */
private fun resolveMenuItemLabel(labelResource: Int?, userProvidedText: String?): String? =
    if (labelResource != null) Resources.getString(labelResource) else userProvidedText

private val WHITESPACE_REGEX = Regex("\\s+")

/**
 * Produce a short in-circle stand-in for items that have no icon. Takes the first
 * letter of up to the first two whitespace-separated words so "Gmail" → "G" and
 * "Slack HQ" → "SH".
 */
private fun circleInitials(source: String): String {
    val tokens = source.trim().split(WHITESPACE_REGEX)
    return when {
        tokens.isEmpty() || tokens[0].isEmpty() -> ""
        tokens.size == 1 -> tokens[0].first().uppercaseChar().toString()
        else -> (tokens[0].first().uppercaseChar().toString() +
            tokens[1].first().uppercaseChar().toString())
    }
}
