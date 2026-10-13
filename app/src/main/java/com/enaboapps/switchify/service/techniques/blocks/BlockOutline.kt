package com.enaboapps.switchify.service.techniques.blocks

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.RelativeLayout
import androidx.core.graphics.toColorInt
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.scanning.ScanVisualConstants
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.utils.HighlightAnimations
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * Draws the rounded outline around the block a technique is refining inside.
 * The outline lives on the owning technique UI's overlay so it stacks with that
 * technique's own cursor views.
 */
class BlockOutline(
    private val context: Context,
    private val ui: AccessTechniqueUIBase
) {
    private var outline: RelativeLayout? = null

    private val strokePx = ScreenUtils.dpToPx(context, ScanVisualConstants.CURSOR_LINE_DP)

    fun show(block: ScanBlock?) {
        if (block == null) {
            remove()
            return
        }
        val existing = outline
        if (existing == null) {
            val view = RelativeLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = ScreenUtils.dpToPxFloat(context, ScanVisualConstants.CORNER_RADIUS_DP)
                    setStroke(
                        strokePx,
                        ScanColorManager.getScanColorSetFromPreferences(context).primaryColor.toColorInt()
                    )
                }
            }
            outline = view
            ui.addView(view, block.left, block.top, block.width, block.height)
            HighlightAnimations.fadeIn(view)
        } else {
            ui.updateView(existing, block.left, block.top, block.width, block.height)
        }
    }

    fun remove() {
        outline?.let { view ->
            try {
                if (view.parent != null) {
                    ui.removeView(view)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                outline = null
            }
        }
    }

    fun clear() {
        outline = null
    }
}
