package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import androidx.core.graphics.toColorInt
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.RelativeLayout
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.scanning.ScanColorManager
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import java.lang.ref.WeakReference

class CurrentPointVisual(context: Context) {

    private val contextRef: WeakReference<Context> = WeakReference(context)
    private var circle: WeakReference<RelativeLayout>? = null
    private var showing = false

    fun showCurrentPoint(x: Int = GesturePoint.x, y: Int = GesturePoint.y) {
        if (!showing) {
            val context = contextRef.get() ?: return

            val gradientDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    ScanColorManager.getScanColorSetFromPreferences(context).secondaryColor.toColorInt()
                )
                setSize(60, 60)
            }

            val imageView = ImageView(context).apply {
                setImageDrawable(gradientDrawable)
            }

            val circleLayout = RelativeLayout(context).apply {
                addView(imageView, RelativeLayout.LayoutParams(60, 60))
            }

            circle = WeakReference(circleLayout)
            showing = true

            // Add the circle to the accessibility window
            SwitchifyAccessibilityWindow.instance.addView(
                circleLayout,
                x - 60 / 2,
                y - 60 / 2,
                60,
                60
            )
        }
    }

    fun hideCurrentPoint() {
        if (showing) {
            circle?.get()?.let { circle ->
                SwitchifyAccessibilityWindow.instance.removeView(circle)
            }
            circle = null
            showing = false
        }
    }

}