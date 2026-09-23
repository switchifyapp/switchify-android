package com.enaboapps.switchify.service.menu

import android.content.ComponentCallbacks
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.scanning.tree.ScanTree
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.MenuHighlightHud
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

interface MenuViewListener {
    fun onMenuViewClosed()
}

class MenuView(val context: Context, private val menu: BaseMenu) {
    val menuId: String? get() = menu.menuId
    var menuViewListener: MenuViewListener? = null
    val scanTree = ScanTree(context, visualEffectsEnabled = true)
    private val preferenceManager = PreferenceManager(context)
    private var baseLayout = LinearLayout(context)
    private var currentPage = 0
    private var retainedItems: List<MenuItem>? = null
    private var menuPages = emptyList<MenuPage>()
    private var scope: CoroutineScope? = null
    private val renderGeneration = MenuRenderGeneration()
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var pendingTree = false
    private var lastPositionedSize: Pair<Int, Int>? = null
    private var reflowPending = false

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == PreferenceManager.PREFERENCE_KEY_MENU_SIZE_SCALE ||
            key == PreferenceManager.PREFERENCE_KEY_MENU_TRANSPARENCY ||
            key == PreferenceManager.PREFERENCE_KEY_ROW_COLUMN_SCAN ||
            key == PreferenceManager.PREFERENCE_KEY_GROUP_SCAN) requestReflow()
    }

    private val configurationListener = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            requestReflow()
        }
        @Deprecated("No menu-specific low-memory work")
        override fun onLowMemory() = Unit
    }

    private fun setup() {
        val generation = renderGeneration.current
        scope?.launch {
            val staticItems = menu.getMenuItems()
            val items = try {
                staticItems + menu.getDynamicMenuItems().orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.log(LogEvent.MenuDynamicItemsLoadFailed,
                    data = mapOf("result" to "failure", "reason" to "dynamic_items_exception",
                        "menu_id" to menuId, "static_items_count" to staticItems.size),
                    throwable = failure)
                staticItems
            }
            if (!renderGeneration.isCurrent(generation)) return@launch
            retainedItems = items
            createMenuPages(items)
            inflateMenu()
        }
    }

    private fun createMenuPages(items: List<MenuItem>) {
        val contextualIds = menu.contextualItemIds()
        val contextual = items.filter { it.id in contextualIds }
        val back = items.firstOrNull { it.id == "previous_menu_first" }
        val content = items.filterNot { it.id in contextualIds || it === back }
        val close = if (menu.shouldShowNavMenuItems()) menu.buildCloseItem() else null
        val title = MenuConstants.getTitleResource(menu.menuId)
            ?.takeIf { MenuGridMeasurer.showsTitle(context, menu.menuId) }
        var metrics = MenuGridMeasurer.measure(context, content, contextual.size,
            title != null, close != null || back != null)
        if (metrics.grid.pages(content).size > 1 && close == null && back == null) {
            metrics = MenuGridMeasurer.measure(context, content, contextual.size, title != null, true)
        }
        val pages = metrics.grid.pages(content)
        currentPage = metrics.grid.clampPage(currentPage, content.size)
        menuPages = pages.mapIndexed { index, page ->
            MenuPage(context, page, contextual, close, back, metrics, title,
                index, pages.lastIndex, ::onMenuPageChanged)
        }
    }

    private fun requestReflow() {
        if (reflowPending || !renderGeneration.isOpen) return
        reflowPending = true
        val generation = renderGeneration.current
        scope?.launch {
            yield()
            reflowPending = false
            if (!renderGeneration.isCurrent(generation)) return@launch
            retainedItems?.let {
                menuPages.forEach(MenuPage::releaseViews)
                createMenuPages(it)
                inflateMenu()
            }
        }
    }

    private fun onMenuPageChanged(page: Int) {
        if (!renderGeneration.isOpen || page !in menuPages.indices || page == currentPage) return
        currentPage = page
        inflateMenu()
    }

    private fun inflateMenu() {
        renderGeneration.next()
        scanTree.clearTree()
        baseLayout.removeAllViews()
        menuPages.forEach(MenuPage::releaseViews)
        lastPositionedSize = null
        pendingTree = true
        val page = menuPages.getOrNull(currentPage) ?: return
        val transparent = preferenceManager.getBooleanValue(PreferenceManager.PREFERENCE_KEY_MENU_TRANSPARENCY)
        baseLayout.addView(page.getMenuLayout(transparent), ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }

    private fun observeLayout() {
        val observedLayout = baseLayout
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (renderGeneration.isOpen && observedLayout === baseLayout) {
                if (!reflowPending) {
                    val size = baseLayout.width to baseLayout.height
                    if (size != lastPositionedSize && size.first > 0 && size.second > 0) {
                        lastPositionedSize = size
                        pendingTree = true
                        scanTree.clearTree()
                        resizeAndRepositionMenu()
                        baseLayout.postInvalidateOnAnimation()
                    } else if (pendingTree && !baseLayout.isLayoutRequested) {
                        val page = menuPages.getOrNull(currentPage)
                        if (page != null && page.isMeasured()) {
                            pendingTree = false
                            scanTree.buildMenuRows(page.translateMenuRowsToNodes())
                            MenuManager.getInstance().notifyMenuNodesChanged(this)
                        }
                    }
                }
            }
            true
        }
        preDrawListener = listener
        baseLayout.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun resizeAndRepositionMenu() {
        // Clamp within the area the overlay window can draw into, which
        // excludes the navigation bar.
        val screenWidth = MenuSurfaceBudget.usableWidthPx(context)
        val screenHeight = MenuSurfaceBudget.usableHeightPx(context)
        val menuWidth = baseLayout.width
        val menuHeight = baseLayout.height

        // The MenuHighlightHud occupies the top of the screen; keep the
        // menu surface below its footprint so the two never overlap.
        val topReserved = MenuHighlightHud.reservedTopPx(context)

        val offset = 50
        val gesturePoint = GesturePoint.getPoint()
        val preferredX = if (gesturePoint.x + menuWidth + offset > screenWidth) {
            screenWidth - menuWidth.toFloat() - offset
        } else {
            gesturePoint.x + offset
        }
        val x = MenuHorizontalPositionCalculator.clamp(
            preferredX = preferredX.toInt(),
            menuWidth = menuWidth,
            screenWidth = screenWidth
        )
        val preferredY = if (gesturePoint.y + menuHeight + offset > screenHeight) {
            gesturePoint.y - menuHeight - offset
        } else {
            gesturePoint.y + offset
        }
        val y = MenuVerticalPositionCalculator.clamp(
            preferredY = preferredY.toInt(),
            menuHeight = menuHeight,
            screenHeight = screenHeight,
            topReserved = topReserved
        )

        MenuViewHandler.instance.updateView(
            baseLayout,
            x,
            y,
            WRAP_CONTENT,
            WRAP_CONTENT
        )
    }

    fun open(scanningManager: ScanningManager) {
        if (renderGeneration.isOpen) return
        renderGeneration.open()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scanningManager.setMenuType()
        baseLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }
        context.registerComponentCallbacks(configurationListener)
        preferenceManager.registerChangeListener(preferenceListener)
        MenuViewHandler.instance.setup(context)
        MenuViewHandler.instance.addViewOffScreen(baseLayout)
        observeLayout()
        val items = retainedItems
        if (items == null) setup() else {
            createMenuPages(items)
            inflateMenu()
        }
    }

    fun getSelectableNodes(): List<ScanNodeInterface> =
        if (renderGeneration.isOpen && !pendingTree) {
            menuPages.getOrNull(currentPage)?.translateMenuItemsToNodes().orEmpty()
        } else emptyList()

    fun close() {
        if (!renderGeneration.isOpen) return
        renderGeneration.close()
        scope?.cancel()
        scope = null
        reflowPending = false
        pendingTree = false
        context.unregisterComponentCallbacks(configurationListener)
        preferenceManager.unregisterChangeListener(preferenceListener)
        preDrawListener?.let {
            if (baseLayout.viewTreeObserver.isAlive) baseLayout.viewTreeObserver.removeOnPreDrawListener(it)
        }
        preDrawListener = null
        lastPositionedSize = null
        baseLayout.removeAllViews()
        menuPages.forEach(MenuPage::releaseViews)
        menuPages = emptyList()
        MenuViewHandler.instance.kill()
        MenuHighlightHud.instance.hide()
        scanTree.cleanup()
        menuViewListener?.onMenuViewClosed()
    }
}
