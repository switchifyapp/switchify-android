package com.enaboapps.switchify.service.menu

import android.content.Context
import android.view.Gravity
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
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * Renders a single radial "page" of the service menu.
 *
 * Content items lay out on a ring with an empty centre. A horizontal nav row
 * sits below the ring containing (in order) prev-page, close, next-page — the
 * close button always shows; prev/next only appear when pagination is active.
 *
 * @property context Accessibility-service context used for view inflation.
 * @property contentItems The ring items for this page (≤ [MenuConstants.RADIAL_ITEMS_PER_PAGE]).
 * @property closeItem The close-menu item for the bottom nav row. Null hides
 *                     the close button for this page (rare — usually set).
 * @property pageIndex This page's index within the parent menu.
 * @property maxPageIndex The last page's index — used to decide whether to
 *                        render a "next" arrow.
 * @property onMenuPageChanged Callback invoked when prev/next is selected.
 */
class MenuPage(
    val context: Context,
    private val contentItems: List<MenuItem>,
    private val closeItem: MenuItem?,
    private val pageIndex: Int,
    private val maxPageIndex: Int,
    val onMenuPageChanged: (pageIndex: Int) -> Unit
) {
    private var prevPageMenuItem: MenuItem? = null
    private var nextPageMenuItem: MenuItem? = null

    /** True when this page has sibling pages — prev/next arrows are rendered. */
    private val hasPagination: Boolean
        get() = maxPageIndex > 0

    /**
     * Get every menu item that lives on this page. Order here feeds the default
     * spatial scanner: ring content items first, then the nav-row items
     * (prev/close/next). The spatial scanner groups/sorts these by (x, y)
     * itself, so the list order is only a fallback for non-spatial consumers.
     */
    fun getMenuItems(): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        items.addAll(contentItems)
        prevPageMenuItem?.let { items.add(it) }
        closeItem?.let { items.add(it) }
        nextPageMenuItem?.let { items.add(it) }
        return items
    }

    /** Translate every menu item on the page to a Node for the scan tree. */
    fun translateMenuItemsToNodes(): List<Node> = getMenuItems().map { Node.fromMenuItem(it) }

    /**
     * Build the composed view that [MenuView] attaches as this page's layout.
     * The container is a vertical LinearLayout: radial ring on top, nav row
     * below. Both sit inside [MenuPageBackground].
     */
    fun getMenuLayout(isTransparent: Boolean): ViewGroup {
        prevPageMenuItem = null
        nextPageMenuItem = null

        val container = createContainerLayout()

        val ring = RadialMenuLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
            maxWidthPx = (ScreenUtils.getWidth(context) - ScreenUtils.dpToPx(context, 40))
                .coerceAtLeast(0)
            minChordGapPx = ScreenUtils.dpToPx(context, 12)
        }

        val radialItemSize = MenuSizeManager.getRadialItemSize(context)
        contentItems.forEach { it.inflate(ring, radialItemSize) }
        // No centre child — the ring centre is intentionally empty; close
        // lives in the nav row below.

        container.addView(ring)

        // Nav row always renders because it hosts the close button. Prev/next
        // arrows join close only when pagination is active.
        val navRow = buildNavRow()
        if (navRow.childCount > 0) {
            container.addView(navRow)
        }

        return AccessibilityComposeView(context) {
            MenuPageBackground(isTransparent) {
                AndroidView(factory = { container })
            }
        }
    }

    /** Create the vertical container that holds the ring and the nav row. */
    private fun createContainerLayout(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Build the bottom nav row: [prev] [close] [next] in that order. Prev/next
     * are elided when they aren't applicable (first/last page, or single page).
     * Returns an empty LinearLayout if nothing belongs here — caller should
     * skip adding it in that case.
     */
    private fun buildNavRow(): LinearLayout {
        val navRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = ScreenUtils.dpToPx(context, 16)
            }
        }

        val smallSize = MenuSizeManager.getSmallItemSize(context)

        if (hasPagination && pageIndex > 0) {
            prevPageMenuItem = MenuItem(
                id = "prevPage",
                drawableId = R.drawable.ic_previous_menu_page,
                labelResource = R.string.menu_item_previous_page,
                showLabelAsDescription = false,
                isSmall = true,
                closeOnSelect = false,
                isMenuHierarchyManipulator = true,
                action = { previousPage() }
            ).also { it.inflate(navRow, smallSize) }
        }

        closeItem?.inflate(navRow, smallSize)

        if (hasPagination && pageIndex < maxPageIndex) {
            nextPageMenuItem = MenuItem(
                id = "nextPage",
                drawableId = R.drawable.ic_next_menu_page,
                labelResource = R.string.menu_item_next_page,
                showLabelAsDescription = false,
                isSmall = true,
                closeOnSelect = false,
                isMenuHierarchyManipulator = true,
                action = { nextPage() }
            ).also { it.inflate(navRow, smallSize) }
        }

        return navRow
    }

    private fun previousPage() {
        val newIndex = if (pageIndex == 0) maxPageIndex else pageIndex - 1
        onMenuPageChanged(newIndex)
    }

    private fun nextPage() {
        val newIndex = if (pageIndex == maxPageIndex) 0 else pageIndex + 1
        onMenuPageChanged(newIndex)
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
