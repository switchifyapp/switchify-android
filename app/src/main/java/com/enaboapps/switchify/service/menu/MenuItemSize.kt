package com.enaboapps.switchify.service.menu

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Per-device size profile for menu items.
 *
 * Two flavours of profile exist:
 *  - **Small** profiles (`PHONE_SMALL`, `TABLET_SMALL`) — used by the nav-row
 *    items (prev / close / next), which keep their fixed-cell shape.
 *  - **Content** profiles (`PHONE_COMPACT`, `PHONE`, `TABLET`,
 *    `TABLET_LARGE`) — used by the main list rows. Per-content-profile
 *    fields (`containerCircleSize`, `itemsPerPage`, `labelTextSize`) drive
 *    the row layout.
 */
data class MenuItemSize(
    val width: Dp,
    val height: Dp,
    val iconSize: Dp,
    val primaryTextSize: TextUnit,
    val secondaryTextSize: TextUnit,
    val padding: Dp,
    val cornerRadius: Dp,
    val elementSpacing: Dp,
    val navigationIconSize: Dp,
    val navigationCircleSize: Dp,
    /**
     * Diameter of the coloured circle on the left of each list row.
     * Small (nav-row) profiles leave this at 0 dp; the nav-row items don't
     * use it.
     */
    val containerCircleSize: Dp = 0.dp,
    /**
     * Max number of list rows a single page can show. Only meaningful on
     * content profiles; nav-row profiles leave the default.
     */
    val itemsPerPage: Int = 6,
    /**
     * Font size for the row label rendered next to the circle.
     */
    val labelTextSize: TextUnit = 14.sp
) {
    /**
     * Row height (in dp, unscaled value) for a list row. Sized to fit the
     * profile's circle plus 24 dp of vertical breathing room — accounts for
     * the 4 dp outer gap between adjacent row backgrounds (8 dp total) and
     * still leaves 8 dp around the circle inside its rounded background.
     */
    val rowHeightDp: Int
        get() = (containerCircleSize.value + 24f).toInt()

    /**
     * Returns a copy of this profile with every Dp/Sp dimension multiplied by
     * [percent] / 100. `itemsPerPage` is preserved — it is a count, not a
     * size. A [percent] of 100 returns this instance unchanged for cheap
     * defaulting.
     */
    fun scaledBy(percent: Int): MenuItemSize {
        if (percent == 100) return this
        val f = percent / 100f
        return copy(
            width = width * f,
            height = height * f,
            iconSize = iconSize * f,
            primaryTextSize = primaryTextSize * f,
            secondaryTextSize = secondaryTextSize * f,
            padding = padding * f,
            cornerRadius = cornerRadius * f,
            elementSpacing = elementSpacing * f,
            navigationIconSize = navigationIconSize * f,
            navigationCircleSize = navigationCircleSize * f,
            containerCircleSize = containerCircleSize * f,
            labelTextSize = labelTextSize * f
        )
    }
}

/**
 * Enum class defining the different size variants available for menu items.
 *
 * Small variants drive the nav-row items; the rest are picked per device by
 * [MenuSizeManager.getItemSizeVariant].
 */
enum class MenuSizeVariant {
    PHONE_SMALL,
    PHONE_COMPACT,
    PHONE,
    TABLET_SMALL,
    TABLET,
    TABLET_LARGE
}

/**
 * Object containing predefined size configurations for different menu item variants
 */
object MenuSizes {

    /**
     * Phone Small - Compact size for small phone screens (nav row).
     */
    val PHONE_SMALL = MenuItemSize(
        width = 70.dp,
        height = 50.dp,
        iconSize = 24.dp,
        primaryTextSize = 10.sp,
        secondaryTextSize = 8.sp,
        padding = 4.dp,
        cornerRadius = 12.dp,
        elementSpacing = 2.dp,
        navigationIconSize = 16.dp,
        navigationCircleSize = 32.dp
    )

    /**
     * Phone Compact — tightest content profile for narrow phones
     * (smallestScreenWidthDp < 360 dp). 40 dp circle, 5 rows per page.
     */
    val PHONE_COMPACT = MenuItemSize(
        width = 62.dp,
        height = 76.dp,
        iconSize = 20.dp,
        primaryTextSize = 9.sp,
        secondaryTextSize = 8.sp,
        padding = 2.dp,
        cornerRadius = 12.dp,
        elementSpacing = 4.dp,
        navigationIconSize = 18.dp,
        navigationCircleSize = 36.dp,
        containerCircleSize = 40.dp,
        itemsPerPage = 5,
        labelTextSize = 10.sp
    )

    /**
     * Phone — content profile for regular phones (360 ≤ swdp < 600).
     * 48 dp circle, 6 rows per page.
     */
    val PHONE = MenuItemSize(
        width = 72.dp,
        height = 86.dp,
        iconSize = 24.dp,
        primaryTextSize = 10.sp,
        secondaryTextSize = 8.sp,
        padding = 2.dp,
        cornerRadius = 14.dp,
        elementSpacing = 6.dp,
        navigationIconSize = 20.dp,
        navigationCircleSize = 40.dp,
        containerCircleSize = 48.dp,
        itemsPerPage = 6,
        labelTextSize = 12.sp
    )

    /**
     * Tablet Small - Current small size (nav row on tablets).
     */
    val TABLET_SMALL = MenuItemSize(
        width = 90.dp,
        height = 60.dp,
        iconSize = 48.dp,
        primaryTextSize = 14.sp,
        secondaryTextSize = 12.sp,
        padding = 4.dp,
        cornerRadius = 20.dp,
        elementSpacing = 0.dp,
        navigationIconSize = 24.dp,
        navigationCircleSize = 48.dp
    )

    /**
     * Tablet — content profile for tablets (600 ≤ swdp < 840).
     * 64 dp circle, 8 rows per page.
     */
    val TABLET = MenuItemSize(
        width = 96.dp,
        height = 108.dp,
        iconSize = 32.dp,
        primaryTextSize = 12.sp,
        secondaryTextSize = 10.sp,
        padding = 2.dp,
        cornerRadius = 18.dp,
        elementSpacing = 6.dp,
        navigationIconSize = 24.dp,
        navigationCircleSize = 48.dp,
        containerCircleSize = 64.dp,
        itemsPerPage = 8,
        labelTextSize = 14.sp
    )

    /**
     * Tablet Large — most spacious content profile for large tablets /
     * foldables (smallestScreenWidthDp ≥ 840 dp). 76 dp circle, 10 rows per
     * page.
     */
    val TABLET_LARGE = MenuItemSize(
        width = 112.dp,
        height = 128.dp,
        iconSize = 40.dp,
        primaryTextSize = 13.sp,
        secondaryTextSize = 11.sp,
        padding = 3.dp,
        cornerRadius = 20.dp,
        elementSpacing = 8.dp,
        navigationIconSize = 28.dp,
        navigationCircleSize = 56.dp,
        containerCircleSize = 76.dp,
        itemsPerPage = 10,
        labelTextSize = 16.sp
    )

    /**
     * Get the appropriate size variant based on the given MenuSizeVariant
     */
    fun getSizeForVariant(variant: MenuSizeVariant): MenuItemSize {
        return when (variant) {
            MenuSizeVariant.PHONE_SMALL -> PHONE_SMALL
            MenuSizeVariant.PHONE_COMPACT -> PHONE_COMPACT
            MenuSizeVariant.PHONE -> PHONE
            MenuSizeVariant.TABLET_SMALL -> TABLET_SMALL
            MenuSizeVariant.TABLET -> TABLET
            MenuSizeVariant.TABLET_LARGE -> TABLET_LARGE
        }
    }
}
