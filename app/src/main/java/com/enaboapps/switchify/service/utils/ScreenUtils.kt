package com.enaboapps.switchify.service.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.WindowMetrics

/**
 * This class provides utility functions to get screen dimensions.
 */
class ScreenUtils {

    companion object {
        /**
         * Returns the width of the screen in pixels.
         *
         * @param context The context of the caller.
         * @return The width of the screen in pixels.
         */
        fun getWidth(context: Context): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindowMetrics(context).bounds.width()
            } else {
                getDisplayMetrics(context).widthPixels
            }
        }

        /**
         * Returns the height of the screen in pixels.
         *
         * @param context The context of the caller.
         * @return The height of the screen in pixels.
         */
        fun getHeight(context: Context): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindowMetrics(context).bounds.height()
            } else {
                getDisplayMetrics(context).heightPixels
            }
        }

        /**
         * Converts dp to pixels.
         *
         * @param context The context of the caller.
         * @param dp The value in dp to convert.
         */
        fun dpToPx(context: Context, dp: Int): Int {
            val density = context.resources.displayMetrics.density
            return (dp * density).toInt()
        }

        /**
         * Converts dp to pixels (float precision, for radii etc.).
         *
         * @param context The context of the caller.
         * @param dp The value in dp to convert.
         */
        fun dpToPxFloat(context: Context, dp: Float): Float {
            return dp * context.resources.displayMetrics.density
        }

        /**
         * Retrieves the current window metrics (API 30+).
         *
         * @param context The context of the caller.
         * @return The current window metrics.
         */
        @Suppress("NewApi")
        private fun getWindowMetrics(context: Context): WindowMetrics {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return windowManager.currentWindowMetrics
        }

        /**
         * Retrieves display metrics for older Android versions (API < 30).
         *
         * @param context The context of the caller.
         * @return The display metrics.
         */
        @Suppress("DEPRECATION")
        private fun getDisplayMetrics(context: Context): DisplayMetrics {
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            return displayMetrics
        }

        /**
         * Returns whether or not the device is a tablet.
         *
         * @param context The context of the caller.
         * @return True if the device is a tablet, false otherwise.
         */
        fun isTablet(context: Context): Boolean {
            val configuration = context.resources.configuration
            val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
            val isTabletSize =
                screenLayout == Configuration.SCREENLAYOUT_SIZE_LARGE || screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE
            return isTabletSize
        }
    }

}