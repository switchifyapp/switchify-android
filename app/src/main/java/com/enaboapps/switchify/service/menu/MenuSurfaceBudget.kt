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

    /**
     * Max width the menu *surface* (Material Surface, including its own
     * padding) can occupy. Pulled in from each screen edge by
     * [SCREEN_HORIZONTAL_MARGIN_DP] total so the surface never runs flush to
     * the device edge.
     */
    fun surfaceMaxWidthPx(context: Context): Int {
        val screenWidthPx = usableWidthPx(context)
        val marginPx = ScreenUtils.dpToPx(context, SCREEN_HORIZONTAL_MARGIN_DP)
        return (screenWidthPx - marginPx).coerceAtLeast(0)
    }

    /**
     * Max height the menu surface can occupy. Reserves the
     * [MenuHighlightHud]'s top-of-screen footprint plus a bottom safety
     * margin.
     */
    fun surfaceMaxHeightPx(context: Context): Int {
        val screenHeightPx = usableHeightPx(context)
        val hudReservedPx = MenuHighlightHud.reservedTopPx(context)
        val marginPx = ScreenUtils.dpToPx(context, SCREEN_VERTICAL_MARGIN_DP)
        return (screenHeightPx - hudReservedPx - marginPx).coerceAtLeast(0)
    }

    /**
     * Window width the overlay can actually draw into: full bounds minus any
     * side-mounted navigation bar (landscape three-button nav).
     */
    fun usableWidthPx(context: Context): Int {
        val insets = ScreenUtils.getNavigationBarInsets(context)
        return (ScreenUtils.getWidth(context) - insets.left - insets.right).coerceAtLeast(0)
    }

    /**
     * Window height the overlay can actually draw into: full bounds minus the
     * bottom navigation bar, which the overlay window is inset by.
     */
    fun usableHeightPx(context: Context): Int {
        val insets = ScreenUtils.getNavigationBarInsets(context)
        return (ScreenUtils.getHeight(context) - insets.bottom).coerceAtLeast(0)
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

}
