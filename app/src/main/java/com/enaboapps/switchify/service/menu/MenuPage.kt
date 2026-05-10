package com.enaboapps.switchify.service.menu

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import kotlin.math.ceil

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

    /**
     * Holds the one-line description of whichever menu node is currently
     * highlighted, or null between highlights. The [HighlightHeader] renders
     * this below the name as secondary text so the scanning user gets a
     * plain-language confirmation of the highlighted action.
     */
    private val highlightedDescription = MutableStateFlow<String?>(null)

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
                highlightedDescription.value = highlightedNode.getDescription()
                    .takeIf { it.isNotEmpty() }
            }
            node.onUnhighlight = {
                highlightedLabel.value = null
                highlightedDescription.value = null
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
        // the ring (background padding, highlight header + its bottom gap,
        // optional nav row + its top gap, and a safety margin) so the ring
        // can be clamped to fit. Without this, Extra Large in landscape on
        // small phones could push the menu off the bottom edge. Numbers
        // mirror the Compose layout in MenuPageBackground / MenuPageBody:
        // 18 dp vertical padding on each side of the Surface = 36 dp total,
        // 12 dp Column gap below the header, 16 dp Column gap above the
        // nav row.
        val willShowNavRow = closeItem != null || hasPagination
        val backgroundVerticalPadPx = ScreenUtils.dpToPx(context, 36)
        val headerToRingGapPx = ScreenUtils.dpToPx(context, 12)
        val ringToNavGapPx = ScreenUtils.dpToPx(context, 16)
        val safetyMarginPx = ScreenUtils.dpToPx(context, 24)
        // Header now stacks the name (one line, headerLabelTextSize) on top of
        // the description (one line, secondaryTextSize). The 1.4f multiplier
        // models line-height for each, plus a 4 dp inter-line gap mirroring
        // the spacer in [HighlightHeader].
        val headerHeightPx = ScreenUtils.dpToPx(
            context,
            ceil(
                radialItemSize.headerLabelTextSize.value * 1.4f +
                    radialItemSize.secondaryTextSize.value * 1.4f +
                    4f
            ).toInt()
        )
        val navRowHeightPx = if (willShowNavRow) {
            ScreenUtils.dpToPx(context, smallItemSize.height.value.toInt()) +
                ringToNavGapPx
        } else 0
        val verticalOverheadPx = backgroundVerticalPadPx + headerHeightPx +
            headerToRingGapPx + navRowHeightPx + safetyMarginPx
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

        return AccessibilityComposeView(context) {
            MenuPageBackground(isTransparent) {
                MenuPageBody(
                    ring = ring,
                    navRow = if (showNavRow) navRow else null,
                    highlightedLabel = highlightedLabel,
                    highlightedDescription = highlightedDescription,
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
    highlightedDescription: StateFlow<String?>,
    menuSize: MenuItemSize
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HighlightHeader(
            labelFlow = highlightedLabel,
            descriptionFlow = highlightedDescription,
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
 * Renders the currently highlighted item's label and description as a two-line
 * header above the ring. The name sits on top, the description sits below it
 * as muted secondary text. When [labelFlow] emits `null` (menu just opened, or
 * the scanner is between items) the name shows a muted placeholder and the
 * description renders blank — keeping the header's height stable so the ring
 * below doesn't jump as the scanner moves.
 *
 * Both texts are width-clamped to [MenuItemSize.headerLabelMaxWidth]. The name
 * is locked to a single line; the description is locked to a single line with
 * trailing ellipsis so very long descriptions truncate rather than stretching
 * the Surface or growing the header's vertical footprint.
 */
@Composable
private fun HighlightHeader(
    labelFlow: StateFlow<String?>,
    descriptionFlow: StateFlow<String?>,
    menuSize: MenuItemSize,
    modifier: Modifier = Modifier,
) {
    val label by labelFlow.collectAsState()
    val description by descriptionFlow.collectAsState()
    val isPlaceholder = label == null
    val text = label ?: stringResource(R.string.menu_no_highlighted_item)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(max = menuSize.headerLabelMaxWidth)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (isPlaceholder) 0.5f else 1.0f
            ),
            fontSize = menuSize.headerLabelTextSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Render an empty (but reserved-height) line when no description is
        // available so the surrounding Surface never resizes between scan
        // ticks. We supply a non-empty placeholder string so Compose still
        // reserves a line box; the transparent color hides it.
        val descriptionText = description ?: " "
        Text(
            text = descriptionText,
            color = if (description == null) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = menuSize.secondaryTextSize,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
