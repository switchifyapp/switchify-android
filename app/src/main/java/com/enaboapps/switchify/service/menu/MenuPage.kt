package com.enaboapps.switchify.service.menu

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.R
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
    private var baseLayout: LinearLayout = LinearLayout(context)
    private var prevPageMenuItem: MenuItem? = null
    private var nextPageMenuItem: MenuItem? = null

    init {
        baseLayout.orientation = LinearLayout.VERTICAL
        baseLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val isNightMode = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        baseLayout.background = GradientDrawable().apply {
            setColor(if (isNightMode) Color.BLACK else Color.WHITE)
            cornerRadius = 48f
        }
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
        // Add divider before navigation items
        addDivider()

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
    private fun addDivider() {
        val isNightMode = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        // Create a container for the divider that won't affect parent width
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
        baseLayout.addView(dividerContainer)
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
