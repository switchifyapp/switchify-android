package com.enaboapps.switchify.service.menu

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.utils.ScreenUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    /**
     * Holds the full text of whichever menu node is currently highlighted by
     * the scanner (ring content item or nav-row item), or null when nothing on
     * this page is highlighted. The [HighlightHeader] observes this and
     * renders the label as a header above the ring — falling back to a muted
     * placeholder when the value is null so the header's vertical footprint
     * stays stable as the user scans.
     */
    private val highlightedLabel = MutableStateFlow<String?>(null)

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

    fun translateMenuItemsToNodes(): List<Node> = getMenuItems().map { menuItem ->
        Node.fromMenuItem(menuItem).also { node ->
            node.onHighlight = { highlightedNode ->
                highlightedLabel.value = highlightedNode.getContentDescription()
            }
            node.onUnhighlight = { highlightedLabel.value = null }
        }
    }

    fun getMenuLayout(isTransparent: Boolean): ViewGroup {
        prevPageMenuItem = null
        nextPageMenuItem = null

        val screenWidthPx = ScreenUtils.getWidth(context)
        val edgeInsetPx = ScreenUtils.dpToPx(context, 40)
        val chordGapPx = ScreenUtils.dpToPx(context, 12)
        val radialItemSize = MenuSizeManager.getRadialItemSize(context)
        val smallItemSize = MenuSizeManager.getSmallItemSize(context)

        val ring = RadialMenuLayout(
            context = context,
            minChordGapPx = chordGapPx,
            maxWidthPx = (screenWidthPx - edgeInsetPx).coerceAtLeast(0)
        )
        contentItems.forEach { it.inflate(ring, radialItemSize) }

        val navRow = buildNavRow(smallItemSize)
        val showNavRow = navRow.childCount > 0

        return AccessibilityComposeView(context) {
            MenuPageBackground(isTransparent) {
                MenuPageBody(
                    ring = ring,
                    navRow = if (showNavRow) navRow else null,
                    highlightedLabel = highlightedLabel,
                    menuSize = radialItemSize
                )
            }
        }
    }

    /**
     * Bottom nav row: [prev] [close] [next]. Prev/next are elided when they
     * don't apply (first/last page, single page). Returns an empty LinearLayout
     * if nothing belongs here — caller skips adding it in that case.
     */
    private fun buildNavRow(smallItemSize: MenuItemSize): LinearLayout {
        val navRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
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

/**
 * Vertical stack of the highlight header, the radial ring, and the optional
 * nav row. The ring and nav row are already-built Android views; this
 * composable only handles their placement. The header sits above the ring so
 * long labels never intrude on ring items — previously the label was rendered
 * inside the ring's empty centre, which on phones left too little horizontal
 * clearance between the 3 o'clock and 9 o'clock items for multi-line wraps.
 */
@Composable
private fun MenuPageBody(
    ring: RadialMenuLayout,
    navRow: LinearLayout?,
    highlightedLabel: StateFlow<String?>,
    menuSize: MenuItemSize
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HighlightHeader(
            labelFlow = highlightedLabel,
            menuSize = menuSize,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        AndroidView(factory = { ring })
        if (navRow != null) {
            AndroidView(
                factory = { navRow },
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

/**
 * Renders the currently highlighted item's label as a header above the ring.
 * When [labelFlow] emits `null` (menu just opened, or the scanner is between
 * items) the header shows a muted placeholder — keeping the header's height
 * stable so the ring below doesn't jump.
 *
 * The text is locked to a single line (`maxLines = 1`) with trailing ellipsis
 * so the header's vertical footprint never grows between items with
 * different-length labels — previously a long label would wrap onto a second
 * line and visibly resize the surrounding menu Surface as the scanner moved.
 * [MenuItemSize.headerLabelMaxWidth] caps the horizontal extent so very long
 * labels truncate with `…` rather than stretching the Surface past the ring.
 */
@Composable
private fun HighlightHeader(
    labelFlow: StateFlow<String?>,
    menuSize: MenuItemSize,
    modifier: Modifier = Modifier,
) {
    val label by labelFlow.collectAsState()
    val isPlaceholder = label == null
    val text = label ?: stringResource(R.string.menu_no_highlighted_item)
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface.copy(
            alpha = if (isPlaceholder) 0.5f else 1.0f
        ),
        fontSize = menuSize.headerLabelTextSize,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = menuSize.headerLabelMaxWidth)
            .padding(horizontal = 8.dp),
    )
}
