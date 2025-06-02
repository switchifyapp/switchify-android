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
    val navigationCircleSize: Dp
)

/**
 * Enum class defining the different size variants available for menu items
 */
enum class MenuSizeVariant {
    PHONE_SMALL,
    PHONE_REGULAR,
    TABLET_SMALL,
    TABLET_REGULAR
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
        primaryTextSize = 12.sp,
        secondaryTextSize = 10.sp,
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
        width = 120.dp,
        height = 100.dp,
        iconSize = 36.dp,
        primaryTextSize = 14.sp,
        secondaryTextSize = 12.sp,
        padding = 6.dp,
        cornerRadius = 16.dp,
        elementSpacing = 4.dp,
        navigationIconSize = 20.dp,
        navigationCircleSize = 40.dp
    )
    
    /**
     * Tablet Small - Current small size (navigation items)
     */
    val TABLET_SMALL = MenuItemSize(
        width = 90.dp,
        height = 60.dp,
        iconSize = 48.dp,
        primaryTextSize = 16.sp,
        secondaryTextSize = 14.sp,
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
        primaryTextSize = 16.sp,
        secondaryTextSize = 14.sp,
        padding = 4.dp,
        cornerRadius = 20.dp,
        elementSpacing = 0.dp,
        navigationIconSize = 24.dp,
        navigationCircleSize = 48.dp
    )
    
    /**
     * Get the appropriate size variant based on the given MenuSizeVariant
     */
    fun getSizeForVariant(variant: MenuSizeVariant): MenuItemSize {
        return when (variant) {
            MenuSizeVariant.PHONE_SMALL -> PHONE_SMALL
            MenuSizeVariant.PHONE_REGULAR -> PHONE_REGULAR
            MenuSizeVariant.TABLET_SMALL -> TABLET_SMALL
            MenuSizeVariant.TABLET_REGULAR -> TABLET_REGULAR
        }
    }
}