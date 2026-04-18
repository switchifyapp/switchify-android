package com.enaboapps.switchify.service.menu

import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * This class represents a page of the menu
 * @property context The context of the menu page
 * @property rowsOfMenuItems The rows of menu items
 * @property showNavMenuItems Whether to show navigation menu items
 * @property navItems The navigation items
 * @property pageIndex The index of the page
 * @property maxPageIndex The maximum index of the page
 * @property onMenuPageChanged The action to perform when the page is changed
 */
class MenuPage(
    val context: Context,
    private val rowsOfMenuItems: List<List<MenuItem>>,
    private val showNavMenuItems: Boolean,
    private val navItems: List<MenuItem>,
    private val pageIndex: Int,
    private val maxPageIndex: Int,
    val onMenuPageChanged: (pageIndex: Int) -> Unit
) {
    private var prevPageMenuItem: MenuItem? = null
    private var nextPageMenuItem: MenuItem? = null

    /**
     * Get the menu items of the page
     * @return The menu items of the page
     */
    fun getMenuItems(): List<MenuItem> {
        val menuItems = mutableListOf<MenuItem>()
        rowsOfMenuItems.forEach { rowItems ->
            rowItems.forEach { menuItem ->
                menuItems.add(menuItem)
            }
        }
        if (showNavMenuItems) {
            menuItems.addAll(navItems)
        }
        prevPageMenuItem?.let { menuItems.add(it) }
        nextPageMenuItem?.let { menuItems.add(it) }
        return menuItems
    }

    /**
     * Translate the menu items to nodes
     * @return The nodes of the menu items
     */
    fun translateMenuItemsToNodes(): List<Node> {
        val menuItems = getMenuItems()
        if (com.enaboapps.switchify.BuildConfig.DEBUG) {
            android.util.Log.d(
                "MenuPage",
                "translateMenuItemsToNodes - getMenuItems() returned ${menuItems.size} items"
            )
        }
        val nodes = ArrayList<Node>(menuItems.size)
        menuItems.asSequence().mapTo(nodes) { Node.fromMenuItem(it) }
        if (com.enaboapps.switchify.BuildConfig.DEBUG) {
            android.util.Log.d(
                "MenuPage",
                "translateMenuItemsToNodes - created ${nodes.size} nodes"
            )
        }
        return nodes
    }

    /**
     * Get the layout of the menu
     * @return The layout of the menu
     */
    fun getMenuLayout(isTransparent: Boolean): ViewGroup {
        val contentLayout = createContentLayout()
        prevPageMenuItem = null
        nextPageMenuItem = null

        rowsOfMenuItems.forEach { rowItems ->
            val rowLayout = createRowLayout()
            rowItems.forEach { menuItem ->
                menuItem.inflate(rowLayout)
            }
            contentLayout.addView(rowLayout)
        }

        if (showNavMenuItems) {
            inflateNavItems(contentLayout)
        }

        return AccessibilityComposeView(context) {
            MenuPageBackground(isTransparent) {
                AndroidView(factory = { contentLayout })
            }
        }
    }

    /**
     * This function inflates the navigation items of the page
     */
    private fun inflateNavItems(contentLayout: LinearLayout) {
        addDivider(contentLayout)

        val rowLayout = createRowLayout()
        val navButtonView = createNavButtonView()
        navItems.forEach { menuItem ->
            menuItem.inflate(rowLayout)
        }
        if (pageIndex > 0) {
            prevPageMenuItem = MenuItem(
                id = "prevPage",
                drawableId = R.drawable.ic_previous_menu_page,
                labelResource = R.string.menu_item_previous_page,
                showLabelAsDescription = false,
                isSmall = true,
                closeOnSelect = false,
                isMenuHierarchyManipulator = true, // Apply nav button styling
                action = { previousPage() }
            )
            prevPageMenuItem?.inflate(rowLayout)
        }
        if (pageIndex < maxPageIndex) {
            nextPageMenuItem = MenuItem(
                id = "nextPage",
                drawableId = R.drawable.ic_next_menu_page,
                labelResource = R.string.menu_item_next_page,
                showLabelAsDescription = false,
                isSmall = true,
                closeOnSelect = false,
                isMenuHierarchyManipulator = true, // Apply nav button styling
                action = { nextPage() }
            )
            nextPageMenuItem?.inflate(rowLayout)
        }
        navButtonView.addView(rowLayout)
        contentLayout.addView(navButtonView)
    }

    private fun createContentLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Get the navigation items of the page
     * @return The navigation items of the page
     */
    private fun createNavButtonView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = 32
            }
        }
    }

    /**
     * Create a row layout
     * @return The row layout
     */
    private fun createRowLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        }
    }

    /**
     * Add a divider line to separate navigation items from menu items
     */
    private fun addDivider(contentLayout: LinearLayout) {
        val isNightMode = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        val dividerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 16, 0, 16) // vertical spacing
            }
        }

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ScreenUtils.dpToPx(context, 100), // Fixed width of 100dp
                ScreenUtils.dpToPx(context, 2) // 2dp height
            )
            setBackgroundColor(
                if (isNightMode) "#30FFFFFF".toColorInt() // Semi-transparent white for dark mode
                else "#20000000".toColorInt() // Semi-transparent black for light mode
            )
        }

        dividerContainer.addView(divider)
        contentLayout.addView(dividerContainer)
    }

    /**
     * Go to the previous page
     */
    private fun previousPage() {
        val pageIndex = if (pageIndex == 0) maxPageIndex else pageIndex - 1
        onMenuPageChanged(pageIndex)
    }

    /**
     * Go to the next page
     */
    private fun nextPage() {
        val pageIndex = if (pageIndex == maxPageIndex) 0 else pageIndex + 1
        onMenuPageChanged(pageIndex)
    }
}

@Composable
private fun MenuPageBackground(
    isTransparent: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = if (isTransparent) 0.82f else 0.98f
        ),
        tonalElevation = 6.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = 20.dp,
                vertical = 18.dp
            )
        ) {
            content()
        }
    }
}
