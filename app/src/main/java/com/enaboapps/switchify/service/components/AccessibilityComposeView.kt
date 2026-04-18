package com.enaboapps.switchify.service.components

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme

/**
 * A custom view component that displays Compose content in a RelativeLayout for accessibility services.
 * This allows for flexible positioning of compose views while maintaining accessibility features.
 */
class AccessibilityComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    content: @Composable () -> Unit = {}
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val composeView: ComposeView by lazy {
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
        }
    }

    init {
        layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
        addView(composeView)
        setContent(content)
    }

    /**
     * Sets the Compose content to be displayed in this view.
     * @param content The composable content to display
     */
    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent {
            SwitchifyTheme {
                content()
            }
        }
    }

    /**
     * Updates the position of the view relative to its parent.
     * @param x The x coordinate
     * @param y The y coordinate
     */
    fun updatePosition(x: Int, y: Int) {
        val params = layoutParams as LayoutParams
        params.leftMargin = x
        params.topMargin = y
        layoutParams = params
    }

    /**
     * Updates the size of the view.
     * @param width The width in pixels
     * @param height The height in pixels
     */
    fun updateSize(width: Int, height: Int) {
        val params = layoutParams as LayoutParams
        params.width = width
        params.height = height
        layoutParams = params
    }
}
