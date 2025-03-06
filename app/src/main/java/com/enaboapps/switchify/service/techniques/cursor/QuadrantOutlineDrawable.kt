package com.enaboapps.switchify.service.techniques.cursor

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.enaboapps.switchify.service.scanning.ScanColorManager

class QuadrantOutlineDrawable(context: android.content.Context) : GradientDrawable() {
    init {
        // Transparent background
        setColor(0x00000000)
        // Set the stroke width of the drawable without alpha
        val parsed =
            Color.parseColor(ScanColorManager.getScanColorSetFromPreferences(context).primaryColor)
        setStroke(8, parsed)
    }
}