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
 * Content items lay out on a ring around [centerItem] (the close manipulator by
 * default). When pagination is active, prev/next items are appended below the
 * ring as a small horizontal nav row. This replaces the previous row-based
 * grid layout — see `MenuView.kt` history for that implementation.
 *
 * @property context Accessibility-service context used for view inflation.
 * @property contentItems The ring items for this page (≤ [MenuView.RADIAL_ITEMS_PER_PAGE]).
 * @property centerItem The item to place in the centre of the ring (usually
 *                      the close-menu manipulator). Null means no centre anchor.
 * @property pageIndex This page's index within the parent menu.
 * @property maxPageIndex The last page's index — used to decide whether to
 *                        render a "next" arrow.
 * @property onMenuPageChanged Callback invoked when prev/next is selected.
 */
class MenuPage(
    val context: Context,
    private val contentItems: List<MenuItem>,
    private val centerItem: MenuItem?,
    private val pageIndex: Int,
    private val maxPageIndex: Int,
    val onMenuPageChanged: (pageIndex: Int) -> Unit
) {
    private var prevPageMenuItem: MenuItem? = null
    private var nextPageMenuItem: MenuItem? = null

    /** True when this page has more than one sibling — prev/next are rendered. */
    private val hasPagination: Boolean
        get() = maxPageIndex > 0

    /**
     * Get every menu item that lives on this page, in the order they'd be scanned
     * if the default (non-radial) scanner picked them up. The centre item comes
     * last so scan cycles exit naturally onto the close button after one
     * revolution; prev/next follow at the very end when pagination is active.
     */
    fun getMenuItems(): List<MenuItem> {
        val items = mutableListOf<MenuItem>()
        items.addAll(contentItems)
        centerItem?.let { items.add(it) }
        prevPageMenuItem?.let { items.add(it) }
        nextPageMenuItem?.let { items.add(it) }
        return items
    }

    /** The nodes corresponding to the ring content items (no centre, no nav). */
    fun getContentNodes(): List<Node> = contentItems.map { Node.fromMenuItem(it) }

    /** The node for the centre item, or null when no centre is rendered. */
    fun getCenterNode(): Node? = centerItem?.let { Node.fromMenuItem(it) }

    /** Nodes for pagination prev/next, in scan order. Empty when pagination is off. */
    fun getTrailingNodes(): List<Node> = listOfNotNull(
        prevPageMenuItem?.let { Node.fromMenuItem(it) },
        nextPageMenuItem?.let { Node.fromMenuItem(it) }
    )

    /**
     * Translate every menu item on the page to a Node. Order here is only used
     * as a fallback by the default scanner; the radial scanner sorts ring nodes
     * by polar angle itself.
     */
    fun translateMenuItemsToNodes(): List<Node> = getMenuItems().map { Node.fromMenuItem(it) }

    /**
     * Build the composed view that [MenuView] attaches as this page's layout.
     * The container is a vertical LinearLayout: radial ring on top, optional
     * nav row (prev/next) below. Both sit inside [MenuPageBackground].
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
        }

        // Add ring children first, then the centre, so the centre index is known
        // ahead of measurement.
        val radialItemSize = MenuSizeManager.getRadialItemSize(context)
        val smallItemSize = MenuSizeManager.getSmallItemSize(context)
        contentItems.forEach { it.inflate(ring, radialItemSize) }
        centerItem?.let {
            it.inflate(ring, smallItemSize)
            ring.centerIndex = ring.childCount - 1
        }

        container.addView(ring)

        if (hasPagination) {
            container.addView(buildNavRow())
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
     * Build a short horizontal row of pagination items below the ring. Prev/next
     * items are generated lazily here so [MenuView] sees the same objects when it
     * later calls [getMenuItems].
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

        if (pageIndex > 0) {
            prevPageMenuItem = MenuItem(
                id = "prevPage",
                drawableId = R.drawable.ic_previous_menu_page,
                labelResource = R.string.menu_item_previous_page,
                showLabelAsDescription = false,
                isSmall = true,
                closeOnSelect = false,
                isMenuHierarchyManipulator = true,
                action = { previousPage() }
            ).also { it.inflate(navRow, MenuSizeManager.getSmallItemSize(context)) }
        }
        if (pageIndex < maxPageIndex) {
            nextPageMenuItem = MenuItem(
                id = "nextPage",
                drawableId = R.drawable.ic_next_menu_page,
                labelResource = R.string.menu_item_next_page,
                showLabelAsDescription = false,
                isSmall = true,
                closeOnSelect = false,
                isMenuHierarchyManipulator = true,
                action = { nextPage() }
            ).also { it.inflate(navRow, MenuSizeManager.getSmallItemSize(context)) }
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
