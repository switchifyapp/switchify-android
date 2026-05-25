package com.enaboapps.switchify.service.menu

import android.content.Context
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.MenuHighlightHud

/**
 * Single source of truth for the bounding box the service menu can occupy and
 * the budgets that derive from it. Both [MenuView] (when deciding how many
 * items fit per page) and [MenuPage] (when laying out the list body) consult
 * these functions so the two never disagree.
 *
 * The model is "fixed bounding box from screen": the surface (background +
 * content + nav row) is sized to fit inside
 *   (screen width − horizontal margin) × (screen height − HUD − bottom margin)
 * and the list adapts to that envelope. Pagination on top of that is just
 * "how many rows fit before we need a second page."
 */
object MenuSurfaceBudget {

    private const val SCREEN_HORIZONTAL_MARGIN_DP = 40
    private const val SCREEN_VERTICAL_MARGIN_DP = 24
    private const val SURFACE_HORIZONTAL_PADDING_DP = 40
    private const val SURFACE_VERTICAL_PADDING_DP = 36
    private const val TITLE_ROW_DP = 36
    private const val NAV_ROW_GAP_DP = 16

    /**
     * Max width the menu *surface* (Material Surface, including its own
     * padding) can occupy. Pulled in from each screen edge by
     * [SCREEN_HORIZONTAL_MARGIN_DP] total so the surface never runs flush to
     * the device edge.
     */
    fun surfaceMaxWidthPx(context: Context): Int {
        val screenWidthPx = ScreenUtils.getWidth(context)
        val marginPx = ScreenUtils.dpToPx(context, SCREEN_HORIZONTAL_MARGIN_DP)
        return (screenWidthPx - marginPx).coerceAtLeast(0)
    }

    /**
     * Max height the menu surface can occupy. Reserves the
     * [MenuHighlightHud]'s top-of-screen footprint plus a bottom safety
     * margin.
     */
    fun surfaceMaxHeightPx(context: Context): Int {
        val screenHeightPx = ScreenUtils.getHeight(context)
        val hudReservedPx = MenuHighlightHud.reservedTopPx(context)
        val marginPx = ScreenUtils.dpToPx(context, SCREEN_VERTICAL_MARGIN_DP)
        return (screenHeightPx - hudReservedPx - marginPx).coerceAtLeast(0)
    }

    /**
     * Max width for the *content area* inside the surface (after subtracting
     * the surface's own horizontal padding). This is the width the list
     * LinearLayout should fit into.
     */
    fun contentMaxWidthPx(context: Context): Int {
        val padPx = ScreenUtils.dpToPx(context, SURFACE_HORIZONTAL_PADDING_DP)
        return (surfaceMaxWidthPx(context) - padPx).coerceAtLeast(0)
    }

    /**
     * Max height for the *content body* (the list) inside the surface, after
     * subtracting surface vertical padding, the optional title row, and the
     * optional nav row + its gap above. Pass conservative `true` values if
     * you don't yet know whether they'll appear — the worst case is that the
     * layout uses slightly less than it could have.
     */
    fun contentBodyMaxHeightPx(
        context: Context,
        smallItemSize: MenuItemSize,
        hasTitle: Boolean,
        willShowNavRow: Boolean
    ): Int {
        val titlePx = if (hasTitle) ScreenUtils.dpToPx(context, TITLE_ROW_DP) else 0
        val navPx = if (willShowNavRow) {
            ScreenUtils.dpToPx(context, smallItemSize.height.value.toInt()) +
                ScreenUtils.dpToPx(context, NAV_ROW_GAP_DP)
        } else 0
        val surfacePadPx = ScreenUtils.dpToPx(context, SURFACE_VERTICAL_PADDING_DP)
        return (surfaceMaxHeightPx(context) - surfacePadPx - titlePx - navPx)
            .coerceAtLeast(0)
    }

    /**
     * Height (px) of a single list row for the given item profile.
     */
    fun rowHeightPx(context: Context, itemSize: MenuItemSize): Int =
        ScreenUtils.dpToPx(context, itemSize.rowHeightDp)

    /**
     * Number of list rows that fit on one page given the current screen and
     * item profile, capped at [MenuItemSize.itemsPerPage]. Always at least 1
     * so a single tall item still renders.
     *
     * The pagination caller doesn't yet know if a given menu will have a
     * title or nav row, so callers pass conservative `true` values here. The
     * budget is the same across every page of a menu by design — every page
     * shows or hides the same chrome.
     */
    fun rowsPerPage(
        context: Context,
        itemSize: MenuItemSize,
        smallItemSize: MenuItemSize,
        hasTitle: Boolean,
        willShowNavRow: Boolean
    ): Int {
        val bodyHeightPx = contentBodyMaxHeightPx(
            context = context,
            smallItemSize = smallItemSize,
            hasTitle = hasTitle,
            willShowNavRow = willShowNavRow
        )
        val rowHeightPx = rowHeightPx(context, itemSize)
        val rowsThatFit = if (rowHeightPx > 0) {
            (bodyHeightPx / rowHeightPx).coerceAtLeast(1)
        } else {
            1
        }
        return minOf(itemSize.itemsPerPage, rowsThatFit)
    }
}
