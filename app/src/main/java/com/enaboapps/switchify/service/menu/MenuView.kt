package com.enaboapps.switchify.service.menu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.core.ServiceCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Interface for listening to menu view closure events.
 */
interface MenuViewListener {
    /**
     * Called when the menu view is closed.
     */
    fun onMenuViewClosed()
}

/**
 * MenuView class responsible for managing and displaying the menu interface.
 * This class handles the creation, display, and navigation of menu pages,
 * as well as the dynamic resizing of the menu when pages change.
 * It maintains a fixed position on the screen and tracks the maximum width and height encountered.
 *
 * @property context The application context.
 * @property menu The base menu to be displayed.
 */
class MenuView(
    val context: Context,
    private val menu: BaseMenu
) {
    /** Listener for menu view events */
    var menuViewListener: MenuViewListener? = null

    /** Dynamic items to be added to the menu after the menu is opened */
    private val dynamicItems = mutableListOf<MenuItem>()

    /** Base layout for the menu */
    private var baseLayout = LinearLayout(context)

    /** Current page index */
    private var currentPage = 0

    /** Total number of pages */
    private var numOfPages = 0

    /** List of menu pages */
    private val menuPages = mutableListOf<MenuPage>()

    /** Scan tree for managing menu item selection */
    val scanTree = ScanTree(context)

    /** Tracks the maximum width encountered */
    private var maxWidth = 0

    /** Tracks the maximum height encountered */
    private var maxHeight = 0

    /** Preference manager */
    private val preferenceManager = PreferenceManager(context)

    /** Flag to track if setup has been completed */
    private var isSetupComplete = false

    /**
     * Sets up the menu by retrieving menu items and creating menu pages.
     * This is now called when the menu is opened, not during initialization.
     */
    private fun setup(onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val staticItems = menu.getMenuItems()

            // Check if we need to load dynamic items
            try {
                val dynamicItems = menu.getDynamicMenuItems()
                if (dynamicItems != null) {
                    // We have dynamic items to load
                    val allItems = staticItems + dynamicItems
                    createMenuPages(allItems)
                } else {
                    // No dynamic loading needed, just use static items
                    createMenuPages(staticItems)
                }
            } catch (e: Exception) {
                // If dynamic loading fails, fall back to static items
                e.printStackTrace()
                if (staticItems.isNotEmpty()) {
                    createMenuPages(staticItems)
                }
            }

            isSetupComplete = true
            onComplete()
        }
    }

    /**
     * Creates menu pages from the provided list of menu items.
     *
     * @param menuItems List of MenuItem objects to be displayed in the menu.
     */
    private fun createMenuPages(menuItems: List<MenuItem>) {
        // Get rows per page from preferences, default to 2
        val numOfRowsPerPage = preferenceManager.getIntegerValue(
            PreferenceManager.PREFERENCE_KEY_MENU_ROWS_PER_PAGE,
            2
        )

        // Calculate items per row based on screen width
        val itemsPerRow = calculateItemsPerRow()

        // Calculate total items per page based on rows and items per row
        val numOfItemsPerPage = numOfRowsPerPage * itemsPerRow

        numOfPages = (menuItems.size + numOfItemsPerPage - 1) / numOfItemsPerPage
        for (i in 0 until numOfPages) {
            val start = i * numOfItemsPerPage
            val end = ((i + 1) * numOfItemsPerPage).coerceAtMost(menuItems.size)

            val pageItems = menuItems.subList(start, end)
            val navRowItems = menu.buildNavMenuItems()

            val rows = mutableListOf<List<MenuItem>>()
            pageItems.chunked(itemsPerRow).forEach { rowItems ->
                rows.add(rowItems)
            }

            menuPages.add(
                MenuPage(
                    context,
                    rows,
                    menu.shouldShowNavMenuItems(),
                    navRowItems,
                    i,
                    numOfPages - 1,
                    ::onMenuPageChanged
                )
            )
        }
    }

    /**
     * Calculates the optimal number of items per row based on available screen width.
     * Uses screen width minus 200dp for margins/padding.
     *
     * @return The number of items that can fit per row (minimum 2, maximum 5)
     */
    private fun calculateItemsPerRow(): Int {
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val density = context.resources.displayMetrics.density
        val screenWidthDp = screenWidthPx / density

        // Reserve 200dp for margins and padding
        val availableWidthDp = screenWidthDp - 200

        // Get menu item width based on device type
        val menuItemSize = MenuSizeManager.getRegularItemSize(context)
        val itemWidthDp = menuItemSize.width.value

        // Add spacing between items (12dp)
        val itemSpacing = 12f
        val totalItemWidth = itemWidthDp + itemSpacing

        // Calculate how many items can fit
        val itemsPerRow = (availableWidthDp / totalItemWidth).toInt()

        // Ensure minimum 2 items per row, maximum 5 items per row
        return itemsPerRow.coerceIn(2, 5)
    }

    /**
     * Callback function triggered when a menu page is changed.
     * Updates the current page index and inflates the new menu page.
     *
     * @param pageIndex The index of the new page.
     */
    private fun onMenuPageChanged(pageIndex: Int) {
        currentPage = pageIndex
        inflateMenu()
    }

    /**
     * Inflates the current menu page and sets up the scan tree.
     * This method is responsible for adding the current page's layout to the base layout
     * and setting up a ViewTreeObserver to handle layout changes.
     */
    private fun inflateMenu() {
        scanTree.clearTree()
        baseLayout.removeAllViews()

        val pageExists = currentPage < menuPages.size
        if (pageExists) {
            val pageLayout = menuPages[currentPage].getMenuLayout()
            baseLayout.addView(
                pageLayout,
                ViewGroup.LayoutParams(
                    WRAP_CONTENT,
                    WRAP_CONTENT
                )
            )

            pageLayout.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    pageLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    updateMaxDimensions()
                    resizeAndRepositionMenu()
                }
            })
        }

        // Use coroutine for delayed tree building after layout completion
        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            if (pageExists) {
                scanTree.buildTree(menuPages[currentPage].translateMenuItemsToNodes(), 0)
                
                // Notify head control that menu nodes changed
                ServiceCore.getHeadControlService()?.refreshMenuNodes()
            } else {
                MenuManager.getInstance().closeMenuHierarchy()
            }
        }
    }

    /**
     * Updates the maximum dimensions based on the current layout size.
     */
    private fun updateMaxDimensions() {
        maxWidth = maxWidth.coerceAtLeast(baseLayout.width)
        maxHeight = maxHeight.coerceAtLeast(baseLayout.height)
    }

    /**
     * Creates the base LinearLayout for the menu.
     * Sets the layout parameters to WRAP_CONTENT for both width and height to allow dynamic resizing.
     */
    private fun createLinearLayout() {
        baseLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (preferenceManager.getBooleanValue(PreferenceManager.PREFERENCE_KEY_MENU_TRANSPARENCY)) {
                alpha = 0.8f
            }
            layoutParams = ViewGroup.LayoutParams(
                WRAP_CONTENT,
                WRAP_CONTENT
            )
        }
    }

    /**
     * Resizes and repositions the menu on the screen.
     * This method ensures the menu is properly sized,
     * handling both larger and smaller page transitions.
     * It also ensures the menu is positioned correctly on the screen.
     */
    private fun resizeAndRepositionMenu() {
        baseLayout.post {
            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels

            val offset = 50
            val gesturePoint = GesturePoint.getPoint()
            val x = if (gesturePoint.x + maxWidth + offset > screenWidth) {
                screenWidth - maxWidth.toFloat() - offset
            } else {
                gesturePoint.x + offset
            }
            var y = if (gesturePoint.y + maxHeight + offset > screenHeight) {
                GesturePoint.y - maxHeight.toFloat() - offset
            } else {
                gesturePoint.y + offset
            }

            // If y is negative, move it to the top of the screen
            if (y < 0) {
                y = 0f
            } else if (y + maxHeight > screenHeight) { // If y is greater than the screen height, move it to the bottom of the screen
                y = (screenHeight - maxHeight).toFloat()
            }

            MenuViewHandler.instance.updateView(
                baseLayout,
                x.toInt(),
                y.toInt(),
                WRAP_CONTENT,
                WRAP_CONTENT
            )
        }
    }

    /**
     * Opens the menu.
     * This method initializes the menu, adds it to the window, and inflates the first page.
     *
     * @param scanningManager The ScanningManager instance to set the menu type.
     */
    fun open(scanningManager: ScanningManager) {
        scanningManager.setMenuType()
        createLinearLayout()
        MenuViewHandler.instance.setup(context)
        MenuViewHandler.instance.addViewOffScreen(baseLayout)

        if (!isSetupComplete) {
            // Setup hasn't been done yet, do it now
            setup {
                // Setup complete, now inflate the menu
                inflateMenu()
            }
        } else {
            // Setup already complete, just inflate
            inflateMenu()
        }
    }

    /**
     * Get selectable nodes for head control navigation
     * @return List of nodes that can be selected on the current page
     */
    fun getSelectableNodes(): List<ScanNodeInterface> {
        android.util.Log.d("MenuView", "getSelectableNodes called - currentPage: $currentPage, menuPages.size: ${menuPages.size}")
        return if (currentPage < menuPages.size) {
            val nodes = menuPages[currentPage].translateMenuItemsToNodes()
            android.util.Log.d("MenuView", "translateMenuItemsToNodes returned ${nodes.size} nodes")
            nodes
        } else {
            android.util.Log.d("MenuView", "currentPage >= menuPages.size, returning empty list")
            emptyList()
        }
    }

    /**
     * Closes the menu and performs necessary cleanup.
     * This method removes the menu from the window, shuts down the scan tree,
     * notifies the listener, and resets the max dimensions.
     */
    fun close() {
        baseLayout.removeAllViews()
        MenuViewHandler.instance.kill()
        scanTree.cleanup()
        menuViewListener?.onMenuViewClosed()
        maxWidth = 0
        maxHeight = 0
    }
}
