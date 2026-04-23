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
    val containerCircleSize: Dp = 0.dp
)

/**
 * Enum class defining the different size variants available for menu items
 */
enum class MenuSizeVariant {
    PHONE_SMALL,
    PHONE_REGULAR,
    PHONE_RADIAL,
    TABLET_SMALL,
    TABLET_REGULAR,
    TABLET_RADIAL
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
     * Phone Radial - Circular-tile profile: icon inside a 48 dp coloured circle
     * with the label stacked underneath. Overall cell is 72 × 86 dp so the ring
     * can pack 8 items into a ~330 dp bounding box on a phone.
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
        containerCircleSize = 48.dp
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
     * Tablet Radial - Circular-tile profile: icon inside a 64 dp coloured circle
     * with the label stacked underneath. Overall cell is 96 × 108 dp; an 8-item
     * ring lands around a ~430 dp bounding box on tablets.
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
        containerCircleSize = 64.dp
    )

    /**
     * Get the appropriate size variant based on the given MenuSizeVariant
     */
    fun getSizeForVariant(variant: MenuSizeVariant): MenuItemSize {
        return when (variant) {
            MenuSizeVariant.PHONE_SMALL -> PHONE_SMALL
            MenuSizeVariant.PHONE_REGULAR -> PHONE_REGULAR
            MenuSizeVariant.PHONE_RADIAL -> PHONE_RADIAL
            MenuSizeVariant.TABLET_SMALL -> TABLET_SMALL
            MenuSizeVariant.TABLET_REGULAR -> TABLET_REGULAR
            MenuSizeVariant.TABLET_RADIAL -> TABLET_RADIAL
        }
    }
}