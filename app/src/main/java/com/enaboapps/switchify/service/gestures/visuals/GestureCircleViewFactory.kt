package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout

internal object GestureCircleViewFactory {
    fun createTarget(context: Context): RelativeLayout {
        val tokens = GestureVisualTokens(context)
        val haloDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((tokens.primary and 0x00FFFFFF) or (56 shl 24))
        }
        val coreDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(tokens.primary)
        }
        val halo = ImageView(context).apply {
            setImageDrawable(haloDrawable)
            layoutParams = RelativeLayout.LayoutParams(tokens.targetHalo, tokens.targetHalo)
        }
        val core = ImageView(context).apply {
            setImageDrawable(coreDrawable)
            layoutParams = RelativeLayout.LayoutParams(tokens.targetCore, tokens.targetCore).apply {
                leftMargin = (tokens.targetHalo - tokens.targetCore) / 2
                topMargin = (tokens.targetHalo - tokens.targetCore) / 2
            }
            elevation = tokens.dp(2f).toFloat()
        }
        return RelativeLayout(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(halo)
            addView(core)
        }
    }

}
