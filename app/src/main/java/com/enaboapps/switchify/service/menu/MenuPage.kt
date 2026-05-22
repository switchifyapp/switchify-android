package com.enaboapps.switchify.service.menu

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.MenuHighlightHud

/**
 * Renders a single radial "page" of the service menu.
 *
 * Content items lay out on a ring with an empty centre. A horizontal nav row
 * sits below the ring containing (in order) prev-page, close, next-page — the
 * close button always shows; prev/next only appear when pagination is active.
 *
 * The highlighted item's name and description are surfaced by
 * [MenuHighlightHud] — a separate top-of-screen overlay — rather than inside
 * the menu surface. The scan callbacks push name + description into the HUD
 * via `onHighlight` / `onUnhighlight` on each [Node].
 */
class MenuPage(
    val context: Context,
    private val contentItems: List<MenuItem>,
    private val closeItem: MenuItem?,
    private val titleResId: Int? = null,
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

    fun translateMenuItemsToNodes(): List<Node> = getMenuItems().map { menuItem ->
        Node.fromMenuItem(menuItem).also { node ->
            node.onHighlight = { highlightedNode ->
                MenuHighlightHud.instance.show(
                    name = highlightedNode.getContentDescription(),
                    description = highlightedNode.getDescription()
                )
            }
            node.onUnhighlight = {
                MenuHighlightHud.instance.hide()
            }
        }
    }

    fun getMenuLayout(isTransparent: Boolean): ViewGroup {
        prevPageMenuItem = null
        nextPageMenuItem = null

        val screenWidthPx = ScreenUtils.getWidth(context)
        val screenHeightPx = ScreenUtils.getHeight(context)
        val edgeInsetPx = ScreenUtils.dpToPx(context, 40)
        val chordGapPx = ScreenUtils.dpToPx(context, 12)
        val radialItemSize = MenuSizeManager.getRadialItemSize(context)
        val smallItemSize = MenuSizeManager.getSmallItemSize(context)

        // Reserve vertical space for everything that stacks above and below
        // the ring (background padding, optional nav row + its top gap, and a
        // safety margin) so the ring can be clamped to fit. Numbers mirror
        // the Compose layout in MenuPageBackground / MenuPageBody: 18 dp
        // vertical padding on each side of the Surface = 36 dp total, and a
        // 16 dp Column gap above the nav row. The MenuHighlightHud's
        // top-of-screen footprint is also reserved so the menu surface — which
        // MenuView clamps below the HUD — still has room for its ring.
        val willShowNavRow = closeItem != null || hasPagination
        val hasTitle = titleResId != null
        val backgroundVerticalPadPx = ScreenUtils.dpToPx(context, 36)
        val ringToNavGapPx = ScreenUtils.dpToPx(context, 16)
        val safetyMarginPx = ScreenUtils.dpToPx(context, 24)
        val hudReservedPx = MenuHighlightHud.reservedTopPx(context)
        val titleRowPx = if (hasTitle) ScreenUtils.dpToPx(context, 36) else 0
        val navRowHeightPx = if (willShowNavRow) {
            ScreenUtils.dpToPx(context, smallItemSize.height.value.toInt()) +
                ringToNavGapPx
        } else 0
        val verticalOverheadPx = backgroundVerticalPadPx + navRowHeightPx +
            safetyMarginPx + hudReservedPx + titleRowPx
        val maxHeightForRingPx = (screenHeightPx - verticalOverheadPx)
            .coerceAtLeast(0)

        val ring = RadialMenuLayout(
            context = context,
            minChordGapPx = chordGapPx,
            maxWidthPx = (screenWidthPx - edgeInsetPx).coerceAtLeast(0),
            maxHeightPx = maxHeightForRingPx
        )
        contentItems.forEach { it.inflate(ring, radialItemSize) }

        val navRow = buildNavRow(smallItemSize)
        val showNavRow = navRow.childCount > 0
        val titleText = titleResId?.let { context.getString(it) }

        return AccessibilityComposeView(context) {
            MenuPageBackground(isTransparent) {
                MenuPageBody(
                    title = titleText,
                    content = ring,
                    navRow = if (showNavRow) navRow else null
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
                descriptionResource = R.string.menu_item_previous_page_description,
                action = { previousPage() }
            ).also { it.inflate(navRow, smallItemSize) }
        }

        closeItem?.inflate(navRow, smallItemSize)

        if (hasPagination && pageIndex < maxPageIndex) {
            nextPageMenuItem = pageNavItem(
                id = MenuConstants.ItemIds.Navigation.NEXT_PAGE,
                drawableId = R.drawable.ic_next_menu_page,
                labelResource = R.string.menu_item_next_page,
                descriptionResource = R.string.menu_item_next_page_description,
                action = { nextPage() }
            ).also { it.inflate(navRow, smallItemSize) }
        }

        return navRow
    }

    private fun pageNavItem(
        id: String,
        drawableId: Int,
        labelResource: Int,
        descriptionResource: Int,
        action: () -> Unit
    ): MenuItem = MenuItem(
        id = id,
        drawableId = drawableId,
        labelResource = labelResource,
        descriptionResource = descriptionResource,
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
 * Vertical stack of the optional title, the menu content (radial ring or
 * linear list), and the optional nav row. The highlighted item's name and
 * description are rendered separately by [MenuHighlightHud] at the top of the
 * screen; the title here is the static menu identity (e.g. "Main Menu", "Tap
 * and Hold") so the user always knows which menu they're in.
 */
@Composable
private fun MenuPageBody(
    title: String?,
    content: ViewGroup,
    navRow: LinearLayout?
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        AndroidView(factory = { content })
        if (navRow != null) {
            AndroidView(
                factory = { navRow },
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
