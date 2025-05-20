package com.enaboapps.switchify.service.menu

import android.widget.LinearLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.utils.Logger
import com.enaboapps.switchify.utils.Resources

/**
 * This class represents a menu item
 * @property id The id of the menu item
 * @property textResource The resource id of the text of the menu item
 * @property userProvidedText The text of the menu item if it is user-provided
 * @property drawableId The drawable resource id of the menu item
 * @property drawableDescriptionResource The resource id of the description of the drawable
 * @property showDrawableDescription Whether to show the drawable description
 * @property isSmall Whether the menu item is small
 * @property closeOnSelect Whether the menu should close when the item is selected
 * @property isLinkToMenu Whether the item is a link to another menu
 * @property isMenuHierarchyManipulator Whether the item manipulates the menu hierarchy
 * @property action The action to perform when the item is selected
 */
class MenuItem(
    val id: String,
    val textResource: Int? = null,
    val userProvidedText: String? = null,
    private val drawableId: Int = 0,
    val drawableDescriptionResource: Int? = null,
    val showDrawableDescription: Boolean = true,
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
        val menuSizeManager = MenuSizeManager(linearLayout.context)
        val screenWidth = ScreenUtils.getWidth(linearLayout.context)
        val itemsPerRow = menuSizeManager.getMenuSize().itemsPerPage / 2

        var widthPx =
            ScreenUtils.dpToPx(linearLayout.context, menuSizeManager.getMenuSize().itemWidth)
        var heightPx =
            ScreenUtils.dpToPx(linearLayout.context, menuSizeManager.getMenuSize().itemHeight)

        if (isSmall) {
            widthPx = ScreenUtils.dpToPx(
                linearLayout.context,
                menuSizeManager.getMenuSize().itemWidthSmall
            )
            heightPx = ScreenUtils.dpToPx(
                linearLayout.context,
                menuSizeManager.getMenuSize().itemHeightSmall
            )
        }

        // If width x itemsPerRow is greater than screen width, adjust the width to fit
        if (widthPx * itemsPerRow > screenWidth) {
            widthPx = screenWidth / itemsPerRow
        }

        // If using a drawable description, adjust the height to accommodate the description
        if (drawableDescriptionResource != null) {
            heightPx += ScreenUtils.dpToPx(linearLayout.context, 20)
        }

        composeView = AccessibilityComposeView(linearLayout.context) {
            MenuItemContent(
                textResource = textResource,
                userProvidedText = userProvidedText,
                drawableId = drawableId,
                drawableDescriptionResource = drawableDescriptionResource,
                showDrawableDescription = showDrawableDescription,
                textSize = menuSizeManager.getMenuSize().textSize,
                textSizeWithIcon = menuSizeManager.getMenuSize().textSizeWithIcon,
                onClick = { select() }
            )
        }

        composeView?.let { view ->
            view.layoutParams = LinearLayout.LayoutParams(widthPx, heightPx).apply {
                weight = 1f
            }
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
        Logger.logEvent("Menu item selected: $id")
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
    textResource: Int?,
    userProvidedText: String?,
    drawableId: Int,
    drawableDescriptionResource: Int?,
    showDrawableDescription: Boolean,
    textSize: Float,
    textSizeWithIcon: Float,
    onClick: () -> Unit
) {
    val text = if (textResource != null) Resources.getString(textResource) else userProvidedText

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        shape = RoundedCornerShape(20),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        MenuItemContentInner(
            text = text,
            drawableId = drawableId,
            drawableDescriptionResource = drawableDescriptionResource,
            showDrawableDescription = showDrawableDescription,
            textSize = textSize,
            textSizeWithIcon = textSizeWithIcon,
            onClick = onClick
        )
    }
}

@Composable
private fun MenuItemContentInner(
    text: String?,
    drawableId: Int,
    drawableDescriptionResource: Int?,
    showDrawableDescription: Boolean,
    textSize: Float,
    textSizeWithIcon: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (drawableId != 0) {
            Icon(
                painter = painterResource(id = drawableId),
                contentDescription = drawableDescriptionResource?.let { Resources.getString(it) },
                modifier = Modifier
                    .size(36.dp)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (text != null) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (drawableId != 0) textSizeWithIcon.sp else textSize.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        }

        if (drawableDescriptionResource != null && showDrawableDescription) {
            Text(
                text = Resources.getString(drawableDescriptionResource),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = textSizeWithIcon.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
