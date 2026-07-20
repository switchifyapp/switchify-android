package com.enaboapps.switchify.service.menu

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.ceil

/**
 * Renders a single page of the service menu.
 *
 * Content items lay out top-to-bottom as a vertical list. A horizontal nav
 * row sits below the list containing (in order) prev-page, close, next-page —
 * the close button always shows; prev/next only appear when pagination is
 * active.
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
     * spatial scanner: content items first, then the nav-row items
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

        val itemSize = MenuSizeManager.getItemSize(context)
        val smallItemSize = MenuSizeManager.getSmallItemSize(context)
        val navigationItems = buildNavigationItems()
        val navigationCellWidthPx = navigationCellWidthPx(smallItemSize, navigationItems.size)
        val navRow = buildNavRow(smallItemSize, navigationItems, navigationCellWidthPx)
        val showNavRow = navigationItems.isNotEmpty()
        val titleText = titleResId?.let { context.getString(it) }
        val contentWidthPx = calculateContentWidth(
            itemSize = itemSize,
            navigationWidthPx = navigationItems.size * navigationCellWidthPx,
            title = titleText
        )

        val content: ViewGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                contentWidthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            contentItems.forEach { it.inflate(this, itemSize) }
        }

        return AccessibilityComposeView(context) {
            MenuPageBackground(
                isTransparent = isTransparent,
                surfaceMaxWidthPx = MenuSurfaceBudget.surfaceMaxWidthPx(context)
            ) {
                MenuPageBody(
                    title = titleText,
                    content = content,
                    navRow = if (showNavRow) navRow else null,
                    contentMaxWidthPx = MenuSurfaceBudget.contentMaxWidthPx(context)
                )
            }
        }
    }

    private fun calculateContentWidth(
        itemSize: MenuItemSize,
        navigationWidthPx: Int,
        title: String?
    ): Int {
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                itemSize.labelTextSize.value,
                context.resources.displayMetrics
            )
            // Row labels inherit Material3 bodyLarge letter spacing (the app
            // theme does not override bodyLarge), so the measurement must
            // include it or long labels wrap despite fitting.
            letterSpacing = LABEL_LETTER_SPACING_SP / itemSize.labelTextSize.value
        }
        val circleWidthPx = ScreenUtils.dpToPx(
            context,
            itemSize.containerCircleSize.value.toInt()
        )
        val rowPaddingPx = ScreenUtils.dpToPx(context, ROW_HORIZONTAL_PADDING_DP)
        val labelSpacingPx = ScreenUtils.dpToPx(context, LABEL_SPACING_DP)
        val chevronWidthPx = ScreenUtils.dpToPx(context, CHEVRON_WIDTH_DP)
        val labelTolerancePx = ScreenUtils.dpToPx(context, LABEL_WIDTH_TOLERANCE_DP)
        val rows = contentItems.map { item ->
            MenuRowWidth(
                labelWidthPx = ceil(labelPaint.measureText(item.displayText()).toDouble()).toInt(),
                fixedWidthPx = rowPaddingPx + circleWidthPx + labelSpacingPx + labelTolerancePx +
                    if (item.showsForwardChevron) chevronWidthPx else 0
            )
        }
        val titleWidthPx = title?.let { text ->
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    TITLE_TEXT_SIZE_SP,
                    context.resources.displayMetrics
                )
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = TITLE_LETTER_SPACING_SP / TITLE_TEXT_SIZE_SP
            }
            ceil(titlePaint.measureText(text).toDouble()).toInt()
        } ?: 0
        return MenuContentWidthCalculator.calculate(
            maxWidthPx = MenuSurfaceBudget.contentMaxWidthPx(context),
            minimumWidthPx = maxOf(navigationWidthPx, titleWidthPx),
            rows = rows
        )
    }

    /**
     * Nav-row items in display order: [prev] [close] [next]. Prev/next are
     * elided when they don't apply (first/last page, single page).
     */
    private fun buildNavigationItems(): List<MenuItem> {
        val navigationItems = mutableListOf<MenuItem>()
        if (hasPagination && pageIndex > 0) {
            prevPageMenuItem = pageNavItem(
                id = MenuConstants.ItemIds.Navigation.PREV_PAGE,
                drawableId = R.drawable.ic_previous_menu_page,
                labelResource = R.string.menu_item_previous_page,
                descriptionResource = R.string.menu_item_previous_page_description,
                action = { previousPage() }
            ).also(navigationItems::add)
        }

        closeItem?.let(navigationItems::add)

        if (hasPagination && pageIndex < maxPageIndex) {
            nextPageMenuItem = pageNavItem(
                id = MenuConstants.ItemIds.Navigation.NEXT_PAGE,
                drawableId = R.drawable.ic_next_menu_page,
                labelResource = R.string.menu_item_next_page,
                descriptionResource = R.string.menu_item_next_page_description,
                action = { nextPage() }
            ).also(navigationItems::add)
        }
        return navigationItems
    }

    private fun navigationCellWidthPx(smallItemSize: MenuItemSize, itemCount: Int): Int =
        MenuContentWidthCalculator.navigationCellWidth(
            availableWidthPx = MenuSurfaceBudget.contentMaxWidthPx(context),
            preferredWidthPx = ScreenUtils.dpToPx(
                context,
                smallItemSize.width.value.toInt()
            ),
            minimumTouchWidthPx = ScreenUtils.dpToPx(context, MINIMUM_TOUCH_TARGET_DP),
            itemCount = itemCount
        )

    /**
     * Bottom nav row hosting [navigationItems]. Returns an empty LinearLayout
     * when there are no items — caller skips adding it in that case.
     */
    private fun buildNavRow(
        smallItemSize: MenuItemSize,
        navigationItems: List<MenuItem>,
        cellWidthPx: Int
    ): LinearLayout {
        val navRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        }
        navigationItems.forEach { item ->
            item.inflate(navRow, smallItemSize, cellWidthPx)
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

    private companion object {
        const val ROW_HORIZONTAL_PADDING_DP = 24
        const val LABEL_SPACING_DP = 12
        const val CHEVRON_WIDTH_DP = 24
        const val LABEL_WIDTH_TOLERANCE_DP = 4
        const val MINIMUM_TOUCH_TARGET_DP = 48
        const val TITLE_TEXT_SIZE_SP = 16f

        // Material3 defaults inherited by the rendered text: bodyLarge for
        // row labels, titleMedium for the title.
        const val LABEL_LETTER_SPACING_SP = 0.5f
        const val TITLE_LETTER_SPACING_SP = 0.15f
    }
}

@Composable
private fun MenuPageBackground(
    isTransparent: Boolean,
    surfaceMaxWidthPx: Int,
    content: @Composable () -> Unit
) {
    val surfaceMaxWidth = with(LocalDensity.current) { surfaceMaxWidthPx.toDp() }
    Surface(
        modifier = Modifier.widthIn(max = surfaceMaxWidth),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = if (isTransparent) 0.84f else 0.98f
        ),
        tonalElevation = 3.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
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
 * Vertical stack of the optional title, the menu list, and the optional nav
 * row. The highlighted item's name and description are rendered separately
 * by [MenuHighlightHud] at the top of the screen; the title here is the
 * static menu identity (e.g. "Main Menu", "Tap and Hold") so the user always
 * knows which menu they're in.
 */
@Composable
private fun MenuPageBody(
    title: String?,
    content: ViewGroup,
    navRow: LinearLayout?,
    contentMaxWidthPx: Int
) {
    val contentMaxWidth = with(LocalDensity.current) { contentMaxWidthPx.toDp() }
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
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .padding(bottom = 12.dp)
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
