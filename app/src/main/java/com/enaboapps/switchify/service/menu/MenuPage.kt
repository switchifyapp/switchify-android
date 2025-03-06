package com.enaboapps.switchify.service.menu

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.techniques.nodes.Node

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
    private var baseLayout: LinearLayout = LinearLayout(context)
    private var prevPageMenuItem: MenuItem? = null
    private var nextPageMenuItem: MenuItem? = null

    init {
        baseLayout.orientation = LinearLayout.VERTICAL
        baseLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        baseLayout.setBackgroundResource(R.drawable.menu_background)
        baseLayout.setPadding(50, 50, 50, 50)
    }

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
        val nodes = mutableListOf<Node>()
        getMenuItems().forEach { menuItem ->
            nodes.add(
                Node.fromMenuItem(menuItem)
            )
        }
        return nodes
    }

    /**
     * Get the layout of the menu
     * @return The layout of the menu
     */
    fun getMenuLayout(): LinearLayout {
        baseLayout.removeAllViews()

        rowsOfMenuItems.forEach { rowItems ->
            val rowLayout = createRowLayout()
            rowItems.forEach { menuItem ->
                menuItem.inflate(rowLayout)
            }
            baseLayout.addView(rowLayout)
        }

        if (showNavMenuItems) {
            inflateNavItems()
        }

        return baseLayout
    }

    /**
     * This function inflates the navigation items of the page
     */
    private fun inflateNavItems() {
        val rowLayout = createRowLayout()
        val navButtonView = createNavButtonView()
        navItems.forEach { menuItem ->
            menuItem.inflate(rowLayout)
        }
        if (pageIndex > 0) {
            prevPageMenuItem = MenuItem(
                id = "prevPage",
                drawableId = R.drawable.ic_previous_menu_page,
                drawableDescriptionResource = R.string.menu_item_previous_page,
                showDrawableDescription = false,
                isSmall = true,
                closeOnSelect = false,
                action = { previousPage() }
            )
            prevPageMenuItem?.inflate(rowLayout)
        }
        if (pageIndex < maxPageIndex) {
            nextPageMenuItem = MenuItem(
                id = "nextPage",
                drawableId = R.drawable.ic_next_menu_page,
                drawableDescriptionResource = R.string.menu_item_next_page,
                showDrawableDescription = false,
                isSmall = true,
                closeOnSelect = false,
                action = { nextPage() }
            )
            nextPageMenuItem?.inflate(rowLayout)
        }
        navButtonView.addView(rowLayout)
        baseLayout.addView(navButtonView)
    }

    /**
     * Get the navigation items of the page
     * @return The navigation items of the page
     */
    private fun createNavButtonView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
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
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        }
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
