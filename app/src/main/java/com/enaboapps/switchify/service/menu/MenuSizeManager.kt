package com.enaboapps.switchify.service.menu

import android.content.Context
import android.content.res.Configuration

/**
 * Manager class responsible for determining the appropriate menu item sizes
 * based on device characteristics and user preferences
 */
object MenuSizeManager {

    /**
     * Determines the appropriate size variant for navigation (small) items
     * based on device characteristics
     * @param context The context to determine device characteristics
     * @return The appropriate MenuSizeVariant for small items
     */
    fun getSmallItemSizeVariant(context: Context): MenuSizeVariant {
        return if (isPhoneDevice(context)) {
            MenuSizeVariant.PHONE_SMALL
        } else {
            MenuSizeVariant.TABLET_SMALL
        }
    }

    /**
     * Determines the appropriate size variant for regular (large) items
     * based on device characteristics
     * @param context The context to determine device characteristics
     * @return The appropriate MenuSizeVariant for regular items
     */
    fun getRegularItemSizeVariant(context: Context): MenuSizeVariant {
        return if (isPhoneDevice(context)) {
            MenuSizeVariant.PHONE_REGULAR
        } else {
            MenuSizeVariant.TABLET_REGULAR
        }
    }

    /**
     * Gets the MenuItemSize for navigation (small) items
     * @param context The context to determine device characteristics
     * @return The appropriate MenuItemSize for small items
     */
    fun getSmallItemSize(context: Context): MenuItemSize {
        val variant = getSmallItemSizeVariant(context)
        return MenuSizes.getSizeForVariant(variant)
    }

    /**
     * Gets the MenuItemSize for regular (large) items
     * @param context The context to determine device characteristics
     * @return The appropriate MenuItemSize for regular items
     */
    fun getRegularItemSize(context: Context): MenuItemSize {
        val variant = getRegularItemSizeVariant(context)
        return MenuSizes.getSizeForVariant(variant)
    }

    /**
     * Determines the appropriate size variant for ring-positioned (radial) items
     * across four tiers of screen width:
     *   < 360 dp  — compact phone
     *   360–599   — standard phone
     *   600–839   — tablet
     *   ≥ 840     — large tablet / unfolded foldable
     *
     * Each tier has its own item dimensions and preferred items-per-ring count,
     * read by [com.enaboapps.switchify.service.menu.MenuView.createMenuPages]
     * and the customization screen.
     */
    fun getRadialItemSizeVariant(context: Context): MenuSizeVariant {
        val swdp = context.resources.configuration.smallestScreenWidthDp
        return when {
            swdp < 360 -> MenuSizeVariant.PHONE_COMPACT_RADIAL
            swdp < 600 -> MenuSizeVariant.PHONE_RADIAL
            swdp < 840 -> MenuSizeVariant.TABLET_RADIAL
            else -> MenuSizeVariant.TABLET_LARGE_RADIAL
        }
    }

    /**
     * Gets the MenuItemSize for ring-positioned (radial) items
     * @param context The context to determine device characteristics
     * @return The appropriate MenuItemSize for radial items
     */
    fun getRadialItemSize(context: Context): MenuItemSize {
        val variant = getRadialItemSizeVariant(context)
        return MenuSizes.getSizeForVariant(variant)
    }

    /**
     * Determines if the current device is a phone based on screen characteristics
     * Uses smallestScreenWidthDp to determine device type
     * @param context The context to check device characteristics
     * @return true if the device is considered a phone, false for tablet
     */
    private fun isPhoneDevice(context: Context): Boolean {
        val configuration = context.resources.configuration

        // Use smallestScreenWidthDp to determine device type
        // Phones typically have smallestScreenWidthDp < 600dp
        // Tablets typically have smallestScreenWidthDp >= 600dp
        return configuration.smallestScreenWidthDp < 600
    }

    /**
     * Gets detailed device information for debugging purposes
     * @param context The context to check device characteristics
     * @return A string containing device size information
     */
    fun getDeviceInfo(context: Context): String {
        val configuration = context.resources.configuration
        val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val screenSizeName = when (screenLayout) {
            Configuration.SCREENLAYOUT_SIZE_SMALL -> "Small"
            Configuration.SCREENLAYOUT_SIZE_NORMAL -> "Normal"
            Configuration.SCREENLAYOUT_SIZE_LARGE -> "Large"
            Configuration.SCREENLAYOUT_SIZE_XLARGE -> "XLarge"
            else -> "Undefined"
        }

        return buildString {
            append("Device Type: ${if (isPhoneDevice(context)) "Phone" else "Tablet"}\n")
            append("Screen Size: $screenSizeName\n")
            append("Smallest Width: ${configuration.smallestScreenWidthDp}dp\n")
            append("Screen Width: ${configuration.screenWidthDp}dp\n")
            append("Screen Height: ${configuration.screenHeightDp}dp\n")
            append("Density DPI: ${configuration.densityDpi}\n")
        }
    }
}