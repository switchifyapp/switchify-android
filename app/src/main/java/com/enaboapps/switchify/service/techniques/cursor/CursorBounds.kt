package com.enaboapps.switchify.service.techniques.cursor

import android.content.Context
import com.enaboapps.switchify.service.utils.ScreenUtils

object CursorBounds {
    const val X_MIN = 0
    const val Y_MIN = 0

    /**
     * Width cursor bounds
     * @param context The context
     * @return The width of the cursor bounds
     */
    fun width(context: Context): Int {
        return ScreenUtils.getWidth(context)
    }

    /**
     * Height cursor bounds
     * @param context The context
     * @return The height of the cursor bounds
     */
    fun height(context: Context): Int {
        return ScreenUtils.getHeight(context)
    }
}