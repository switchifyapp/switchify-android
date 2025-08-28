package com.enaboapps.switchify.service.techniques.directcontrol

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase

class DirectControlOverlay(private val context: Context) : AccessTechniqueUIBase() {
    private var pointerView: RelativeLayout? = null
    private val pointerSize = 36

    fun showPointer(x: Int, y: Int) {
        if (pointerView == null) {
            pointerView = RelativeLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        6,
                        ScanColorManager.getScanColorSetFromPreferences(context).primaryColor.toColorInt()
                    )
                }
            }
            pointerView?.let { view ->
                addView(view, x - pointerSize / 2, y - pointerSize / 2, pointerSize, pointerSize)
            }
        } else {
            pointerView?.let { view ->
                updateView(view, x - pointerSize / 2, y - pointerSize / 2, pointerSize, pointerSize)
            }
        }
    }

    fun reset() {
        pointerView?.let { removeView(it) }
        pointerView = null
        hide()
    }
}

