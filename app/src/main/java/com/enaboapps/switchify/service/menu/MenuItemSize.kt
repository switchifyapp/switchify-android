package com.enaboapps.switchify.service.menu

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data class that defines all size configurations for menu items
 * @property width The width of the menu item
 * @property height The height of the menu item
 * @property iconSize The size of icons within the menu item
 * @property primaryTextSize The size of primary text (main label)
 * @property secondaryTextSize The size of secondary text (descriptions)
 * @property padding The padding around the menu item content
 * @property cornerRadius The corner radius for rounded corners
 * @property elementSpacing The spacing between elements within the menu item
 * @property navigationIconSize The size of navigation button icons
 * @property navigationCircleSize The size of the circular background for navigation buttons
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
     * Diameter of the circular container behind the icon on radial ring items.
     * Ignored for grid-style profiles (defaults to 0).
     */
    val containerCircleSize: Dp = 0.dp,
    /**
     * Max number of ring items before a radial page paginates. Only consulted
     * for radial profiles; non-radial profiles leave the default and it is
     * never read.
     */
    val itemsPerRing: Int = 4,
    /**
     * Max number of list items before a list page paginates. Mirrors
     * [itemsPerRing] for the vertical list layout mode.
     */
    val itemsPerList: Int = 6,
    /**
     * Font size for the header above the ring that shows the currently
     * highlighted item's full text (or a muted placeholder when nothing is
     * highlighted). Read by `MenuPage.HighlightHeader`. Only consulted by
     * radial profiles; non-radial profiles ignore it.
     */
    val headerLabelTextSize: TextUnit = 14.sp,
    /**
     * Hard cap on the highlight header's width. The header is rendered on a
     * single line with trailing ellipsis, so this value also sets the point
     * at which long labels truncate with `…` rather than stretching the
     * surrounding Surface past the ring. Sized to roughly match the ring's
     * natural bounding-box width per profile. Radial profiles only.
     */
    val headerLabelMaxWidth: Dp = 240.dp
) {
    /**
     * Row height (in dp, unscaled value) for list-mode items. Sized to fit the
     * profile's circle plus 24 dp of vertical breathing room — accounts for
     * the 4 dp outer gap between adjacent row backgrounds (8 dp total) and
     * still leaves 8 dp around the circle inside its rounded background.
     */
    val listRowHeightDp: Int
        get() = (containerCircleSize.value + 24f).toInt()

    /**
     * Returns a copy of this profile with every Dp/Sp dimension multiplied by
     * [percent] / 100. `itemsPerRing` and `itemsPerList` are preserved — they
     * are counts, not sizes. A [percent] of 100 returns this instance unchanged
     * for cheap defaulting.
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
            headerLabelTextSize = headerLabelTextSize * f,
            headerLabelMaxWidth = headerLabelMaxWidth * f
        )
    }
}

/**
 * Enum class defining the different size variants available for menu items
 */
enum class MenuSizeVariant {
    PHONE_SMALL,
    PHONE_REGULAR,
    PHONE_COMPACT_RADIAL,
    PHONE_RADIAL,
    TABLET_SMALL,
    TABLET_REGULAR,
    TABLET_RADIAL,
    TABLET_LARGE_RADIAL
}

/**
 * Object containing predefined size configurations for different menu item variants
 */
object MenuSizes {

    /**
     * Phone Small - Compact size for small phone screens
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
     * Phone Regular - Standard size for phone screens
     */
    val PHONE_REGULAR = MenuItemSize(
        width = 150.dp,
        height = 130.dp,
        iconSize = 36.dp,
        primaryTextSize = 10.sp,
        secondaryTextSize = 8.sp,
        padding = 6.dp,
        cornerRadius = 16.dp,
        elementSpacing = 4.dp,
        navigationIconSize = 20.dp,
        navigationCircleSize = 40.dp
    )

    /**
     * Phone Compact Radial - Tightest profile for narrow phones (smallestScreenWidthDp < 360 dp).
     * Icon inside a 40 dp circle, 62 × 76 dp cell — keeps a 4-item ring under
     * ~200 dp so it fits a 320 dp screen without clamping-induced overlap.
     */
    val PHONE_COMPACT_RADIAL = MenuItemSize(
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
        itemsPerRing = 4,
        itemsPerList = 5,
        headerLabelTextSize = 10.sp,
        headerLabelMaxWidth = 160.dp
    )

    /**
     * Phone Radial - Circular-tile profile for regular phones (360 ≤ swdp < 600).
     * Icon inside a 48 dp coloured circle, 72 × 86 dp cell. 4-item ring fits in
     * ~226 dp on a phone.
     */
    val PHONE_RADIAL = MenuItemSize(
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
        itemsPerRing = 4,
        itemsPerList = 6,
        headerLabelTextSize = 12.sp,
        headerLabelMaxWidth = 190.dp
    )

    /**
     * Tablet Small - Current small size (navigation items)
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
     * Tablet Regular - Current regular size (main menu items)
     */
    val TABLET_REGULAR = MenuItemSize(
        width = 170.dp,
        height = 120.dp,
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
     * Tablet Radial - Circular-tile profile for tablets (600 ≤ swdp < 840).
     * Icon inside a 64 dp circle, 96 × 108 dp cell. 6-item ring fits in
     * ~348 dp — takes advantage of the bigger canvas for less pagination.
     */
    val TABLET_RADIAL = MenuItemSize(
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
        itemsPerRing = 6,
        itemsPerList = 8,
        headerLabelTextSize = 14.sp,
        headerLabelMaxWidth = 300.dp
    )

    /**
     * Tablet Large Radial - Most spacious profile for large tablets / foldables
     * (smallestScreenWidthDp ≥ 840 dp). Icon inside a 76 dp circle, 112 × 128 dp
     * cell. 8-item ring sits in ~500 dp — uses the screen without feeling lost.
     */
    val TABLET_LARGE_RADIAL = MenuItemSize(
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
        itemsPerRing = 8,
        itemsPerList = 10,
        headerLabelTextSize = 16.sp,
        headerLabelMaxWidth = 420.dp
    )

    /**
     * Get the appropriate size variant based on the given MenuSizeVariant
     */
    fun getSizeForVariant(variant: MenuSizeVariant): MenuItemSize {
        return when (variant) {
            MenuSizeVariant.PHONE_SMALL -> PHONE_SMALL
            MenuSizeVariant.PHONE_REGULAR -> PHONE_REGULAR
            MenuSizeVariant.PHONE_COMPACT_RADIAL -> PHONE_COMPACT_RADIAL
            MenuSizeVariant.PHONE_RADIAL -> PHONE_RADIAL
            MenuSizeVariant.TABLET_SMALL -> TABLET_SMALL
            MenuSizeVariant.TABLET_REGULAR -> TABLET_REGULAR
            MenuSizeVariant.TABLET_RADIAL -> TABLET_RADIAL
            MenuSizeVariant.TABLET_LARGE_RADIAL -> TABLET_LARGE_RADIAL
        }
    }
}