package com.enaboapps.switchify.service.menu

import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.utils.Resources

/**
 * This class represents a menu item
 * @property id The id of the menu item
 * @property labelResource The resource id of the label text (used for both display and accessibility)
 * @property userProvidedText The text of the menu item if it is user-provided
 * @property drawableId The drawable resource id of the menu item
 * @property showLabelAsDescription Whether to show the label as description text below the icon
 * @property isSmall Whether the menu item is small
 * @property closeOnSelect Whether the menu should close when the item is selected
 * @property isLinkToMenu Whether the item is a link to another menu
 * @property isMenuHierarchyManipulator Whether the item manipulates the menu hierarchy
 * @property action The action to perform when the item is selected
 */
class MenuItem(
    val id: String,
    val labelResource: Int? = null,
    val userProvidedText: String? = null,
    private val drawableId: Int = 0,
    val showLabelAsDescription: Boolean = true,
    val isSmall: Boolean = false,
    val closeOnSelect: Boolean = true,
    var isLinkToMenu: Boolean = false,
    var isMenuHierarchyManipulator: Boolean = false,
    private val action: () -> Unit
) {
    private var composeView: AccessibilityComposeView? = null

    /**
     * Inflate the menu item
     * @param linearLayout The linear layout to inflate the menu item into
     */
    fun inflate(linearLayout: LinearLayout) {
        composeView = AccessibilityComposeView(linearLayout.context) {
            MenuItemContent(
                labelResource = labelResource,
                userProvidedText = userProvidedText,
                drawableId = drawableId,
                showLabelAsDescription = showLabelAsDescription,
                isMenuHierarchyManipulator = isMenuHierarchyManipulator,
                isSmall = isSmall,
                isLinkToMenu = isLinkToMenu,
                onClick = { select() }
            )
        }

        composeView?.let { view ->
            // Set fixed width/height based on item type using MenuSizeManager
            val context = linearLayout.context
            val menuSize = if (isMenuHierarchyManipulator || isSmall) {
                MenuSizeManager.getSmallItemSize(context)
            } else {
                MenuSizeManager.getRegularItemSize(context)
            }

            val widthPx = ScreenUtils.dpToPx(context, menuSize.width.value.toInt())
            val heightPx = ScreenUtils.dpToPx(context, menuSize.height.value.toInt())

            view.layoutParams = LinearLayout.LayoutParams(widthPx, heightPx)
            linearLayout.addView(view)
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
    showLabelAsDescription: Boolean,
    isMenuHierarchyManipulator: Boolean,
    isSmall: Boolean,
    isLinkToMenu: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val text = if (labelResource != null) Resources.getString(labelResource) else userProvidedText

    // Get appropriate size based on device type and item type
    val menuSize = if (isMenuHierarchyManipulator || isSmall) {
        MenuSizeManager.getSmallItemSize(context)
    } else {
        MenuSizeManager.getRegularItemSize(context)
    }

    Box(
        modifier = Modifier
            .width(menuSize.width)
            .height(menuSize.height)
    ) {
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
                labelResource = labelResource,
                showLabelAsDescription = showLabelAsDescription,
                menuSize = menuSize,
                isLinkToMenu = isLinkToMenu,
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
    labelResource: Int?,
    showLabelAsDescription: Boolean,
    menuSize: MenuItemSize,
    isLinkToMenu: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(menuSize.padding),
        shape = RoundedCornerShape(menuSize.cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    menuSize.elementSpacing,
                    Alignment.CenterVertically
                )
            ) {
                if (drawableId != 0) {
                    Icon(
                        painter = painterResource(id = drawableId),
                        contentDescription = labelResource?.let { Resources.getString(it) },
                        modifier = Modifier.size(menuSize.iconSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (text != null) {
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = if (drawableId != 0) menuSize.primaryTextSize else menuSize.primaryTextSize,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

            }

            // Add link indicator for menu link items
            if (isLinkToMenu) {
                val indicatorOffset = menuSize.cornerRadius + 2.dp
                Icon(
                    painter = painterResource(id = R.drawable.ic_menu_link),
                    contentDescription = "Opens submenu",
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = -indicatorOffset, y = indicatorOffset),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}