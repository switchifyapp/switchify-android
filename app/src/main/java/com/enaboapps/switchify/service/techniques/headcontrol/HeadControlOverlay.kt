package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase

class HeadControlOverlay(private val context: Context) : AccessTechniqueUIBase() {
    private var pointerView: RelativeLayout? = null
    private val pointerSize = 72

    fun showPointer(x: Int, y: Int) {
        if (pointerView == null) {
            pointerView = RelativeLayout(context).apply {
                val imageView = ImageView(context).apply {
                    setImageResource(R.drawable.ic_head_control_pointer)
                    imageTintList = ColorStateList.valueOf(
                        ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor.toColorInt()
                    )
                    scaleX = 1.2f
                    scaleY = 1.2f
                    alpha = 0.8f
                }
                addView(imageView)
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

    fun hidePointer() {
        pointerView?.let { removeView(it) }
        pointerView = null
        hide()
    }

    fun reset() {
        pointerView?.let { removeView(it) }
        pointerView = null
        hide()
    }
}