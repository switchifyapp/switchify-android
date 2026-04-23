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
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * Renders a single radial "page" of the service menu.
 *
 * Content items lay out on a ring with an empty centre. A horizontal nav row
 * sits below the ring containing (in order) prev-page, close, next-page — the
 * close button always shows; prev/next only appear when pagination is active.
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

    private val hasPagination: Boolean
        get() = maxPageIndex > 0

    /**
     * Get every menu item that lives on this page. Order feeds the default
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

    fun translateMenuItemsToNodes(): List<Node> = getMenuItems().map { Node.fromMenuItem(it) }

    fun getMenuLayout(isTransparent: Boolean): ViewGroup {
        prevPageMenuItem = null
        nextPageMenuItem = null

        val screenWidthPx = ScreenUtils.getWidth(context)
        val edgeInsetPx = ScreenUtils.dpToPx(context, 40)
        val chordGapPx = ScreenUtils.dpToPx(context, 12)
        val navTopMarginPx = ScreenUtils.dpToPx(context, 16)
        val radialItemSize = MenuSizeManager.getRadialItemSize(context)
        val smallItemSize = MenuSizeManager.getSmallItemSize(context)

        val container = createContainerLayout()

        val ring = RadialMenuLayout(
            context = context,
            minChordGapPx = chordGapPx,
            maxWidthPx = (screenWidthPx - edgeInsetPx).coerceAtLeast(0)
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        }
        contentItems.forEach { it.inflate(ring, radialItemSize) }
        container.addView(ring)

        val navRow = buildNavRow(smallItemSize, navTopMarginPx)
        if (navRow.childCount > 0) {
            container.addView(navRow)
        }

        return AccessibilityComposeView(context) {
            MenuPageBackground(isTransparent) {
                AndroidView(factory = { container })
            }
        }
    }

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
     * Bottom nav row: [prev] [close] [next]. Prev/next are elided when they
     * don't apply (first/last page, single page). Returns an empty LinearLayout
     * if nothing belongs here — caller skips adding it in that case.
     */
    private fun buildNavRow(smallItemSize: MenuItemSize, topMarginPx: Int): LinearLayout {
        val navRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = topMarginPx
            }
        }

        if (hasPagination && pageIndex > 0) {
            prevPageMenuItem = pageNavItem(
                id = MenuConstants.ItemIds.Navigation.PREV_PAGE,
                drawableId = R.drawable.ic_previous_menu_page,
                labelResource = R.string.menu_item_previous_page,
                action = { previousPage() }
            ).also { it.inflate(navRow, smallItemSize) }
        }

        closeItem?.inflate(navRow, smallItemSize)

        if (hasPagination && pageIndex < maxPageIndex) {
            nextPageMenuItem = pageNavItem(
                id = MenuConstants.ItemIds.Navigation.NEXT_PAGE,
                drawableId = R.drawable.ic_next_menu_page,
                labelResource = R.string.menu_item_next_page,
                action = { nextPage() }
            ).also { it.inflate(navRow, smallItemSize) }
        }

        return navRow
    }

    private fun pageNavItem(
        id: String,
        drawableId: Int,
        labelResource: Int,
        action: () -> Unit
    ): MenuItem = MenuItem(
        id = id,
        drawableId = drawableId,
        labelResource = labelResource,
        showLabelAsDescription = false,
        isSmall = true,
        closeOnSelect = false,
        isMenuHierarchyManipulator = true,
        action = action
    )

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
