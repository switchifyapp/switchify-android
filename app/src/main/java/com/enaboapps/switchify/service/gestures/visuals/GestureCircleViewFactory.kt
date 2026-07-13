package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.R

internal object GestureCircleViewFactory {
    fun create(context: Context, size: Int): RelativeLayout {
        val primary = ContextCompat.getColor(context, R.color.gesture_visual_primary)
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x20000000)
            setSize(size, size)
        }
        val mainDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(primary)
            setStroke(1, 0x20000000)
            setSize(size, size)
        }
        val shadowView = ImageView(context).apply {
            setImageDrawable(shadowDrawable)
            layoutParams = RelativeLayout.LayoutParams(size, size).apply {
                leftMargin = 2
                topMargin = 2
            }
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val mainView = ImageView(context).apply {
            setImageDrawable(mainDrawable)
            layoutParams = RelativeLayout.LayoutParams(size, size)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        return RelativeLayout(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(shadowView)
            addView(mainView)
        }
    }
}
