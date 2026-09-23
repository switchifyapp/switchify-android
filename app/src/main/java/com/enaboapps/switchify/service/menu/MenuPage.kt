package com.enaboapps.switchify.service.menu

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.enaboapps.switchify.service.window.MenuHighlightHud

internal class MenuPage(
    private val context: Context,
    private val contentItems: List<MenuItem>,
    private val contextualItems: List<MenuItem>,
    private val closeItem: MenuItem?,
    private val backItem: MenuItem?,
    private val metrics: MenuGridMetrics,
    private val titleResId: Int?,
    private val pageIndex: Int,
    private val maxPageIndex: Int,
    private val onMenuPageChanged: (Int) -> Unit
) {
    private val navigationSlots: List<MenuItem?> = if (closeItem != null || backItem != null || maxPageIndex > 0) {
        listOf(
            backItem,
            if (pageIndex > 0) pageNavItem(
                MenuConstants.ItemIds.Navigation.PREV_PAGE, R.drawable.ic_previous_menu_page,
                R.string.menu_item_previous_page, R.string.menu_item_previous_page_description
            ) { onMenuPageChanged(pageIndex - 1) } else null,
            closeItem,
            if (pageIndex < maxPageIndex) pageNavItem(
                MenuConstants.ItemIds.Navigation.NEXT_PAGE, R.drawable.ic_next_menu_page,
                R.string.menu_item_next_page, R.string.menu_item_next_page_description
            ) { onMenuPageChanged(pageIndex + 1) } else null
        )
    } else emptyList()

    private val sections = MenuPageSections(contextualItems, contentItems, navigationSlots)

    private fun itemRows(): List<List<MenuItem>> =
        sections.rows(metrics.grid.columns, metrics.navigationColumns)

    fun getMenuItems(): List<MenuItem> = itemRows().flatten()

    fun translateMenuRowsToNodes(): List<List<Node>> = itemRows().map { row ->
        row.map { item ->
            Node.fromMenuItem(item).also { node ->
                node.onHighlight = { highlighted ->
                    MenuHighlightHud.instance.show(highlighted.getContentDescription(), highlighted.getDescription())
                }
                node.onUnhighlight = { MenuHighlightHud.instance.hide() }
            }
        }
    }

    fun translateMenuItemsToNodes(): List<Node> = translateMenuRowsToNodes().flatten()

    fun isMeasured(): Boolean = getMenuItems().all { it.width > 0 && it.height > 0 }

    fun releaseViews() {
        getMenuItems().forEach { it.releaseView() }
    }

    fun getMenuLayout(isTransparent: Boolean): ViewGroup {
        val size = MenuSizeManager.getItemSize(context)
        fun row(items: List<MenuItem?>, columns: Int, height: Int): LinearLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(metrics.widthPx, height)
                items.forEach { item ->
                    if (item != null) {
                        item.inflateGrid(this, size, metrics.widthPx / columns, height,
                            navigation = item in navigationSlots, isTransparent = isTransparent)
                    } else {
                        addView(View(context).apply {
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            layoutParams = ViewGroup.LayoutParams(metrics.widthPx / columns, height)
                        })
                    }
                }
            }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(metrics.widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            contextualItems.forEach { addView(row(listOf(it), 1, metrics.contextualHeightPx)) }
            metrics.grid.rows(contentItems).forEach { items ->
                addView(row(items, metrics.grid.columns, metrics.grid.cellHeightPx))
            }
        }
        val navigation = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(metrics.widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            navigationSlots.chunked(metrics.navigationColumns).forEach {
                addView(row(it, metrics.navigationColumns, metrics.navigationHeightPx))
            }
        }
        return AccessibilityComposeView(context) {
            GridPageSurface(isTransparent) {
                val density = LocalDensity.current
                Column(
                    modifier = Modifier.width(with(density) { metrics.widthPx.toDp() }),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    titleResId?.let { title ->
                        Text(
                            text = stringResource(title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.height(with(density) { metrics.titleHeightPx.toDp() })
                        )
                    }
                    AndroidView(factory = { content })
                    if (maxPageIndex > 0) {
                        val pageDescription = stringResource(
                            R.string.menu_grid_page_count, pageIndex + 1, maxPageIndex + 1
                        )
                        val dotColor = MaterialTheme.colorScheme.onSurface
                        val inactiveDotColor = dotColor.copy(alpha = 0.4f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(with(density) { metrics.pageCountHeightPx.toDp() })
                                .semantics { contentDescription = pageDescription }
                        ) {
                            val pageCount = maxPageIndex + 1
                            val spacing = minOf(18.dp.toPx(), this.size.width / pageCount)
                            val radius = minOf(4.dp.toPx(), spacing / 3f)
                            val startX = (this.size.width - (pageCount - 1) * spacing) / 2f
                            repeat(pageCount) { index ->
                                val center = Offset(startX + index * spacing, this.size.height / 2f)
                                if (index == pageIndex) {
                                    drawCircle(dotColor, radius, center)
                                } else {
                                    drawCircle(inactiveDotColor, radius, center,
                                        style = Stroke(width = minOf(1.5.dp.toPx(), radius)))
                                }
                            }
                        }
                    }
                    if (navigationSlots.isNotEmpty()) {
                        AndroidView(factory = { navigation }, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    private fun pageNavItem(
        id: String,
        drawableId: Int,
        labelResource: Int,
        descriptionResource: Int,
        action: () -> Unit
    ) = MenuItem(
        id = id, drawableId = drawableId, labelResource = labelResource,
        descriptionResource = descriptionResource, closeOnSelect = false,
        isMenuHierarchyManipulator = true, action = action
    )
}

@Composable
private fun GridPageSurface(isTransparent: Boolean, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isTransparent) 0.84f else 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) { content() }
    }
}
