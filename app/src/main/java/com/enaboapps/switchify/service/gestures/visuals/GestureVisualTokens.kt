package com.enaboapps.switchify.service.gestures.visuals

import android.animation.ValueAnimator
import android.content.Context
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.R
import kotlin.math.roundToInt

internal class GestureVisualTokens(context: Context) {
    private val units = GestureVisualUnitConverter(
        context.resources.displayMetrics.density,
        context.resources.displayMetrics.scaledDensity
    )
    val primary = ContextCompat.getColor(context, R.color.gesture_visual_primary)
    val onPrimary = ContextCompat.getColor(context, R.color.gesture_visual_on_primary)
    val targetCore = dp(24f)
    val targetHalo = dp(36f)
    val touchPoint = dp(28f)
    val pathStroke = dp(6f).toFloat()
    val pathUnderlay = dp(9f).toFloat()
    val pathHead = dp(24f)
    val tapCore = dp(24f)
    val tapHalo = dp(44f)
    val progressContainer = dp(64f)
    val progressStroke = dp(4f).toFloat()
    val labelText = sp(12f)
    val shadowOffset = dp(2f).toFloat()

    fun dp(value: Float): Int = units.dp(value)

    private fun sp(value: Float): Float = units.sp(value)
}

internal class GestureVisualUnitConverter(
    private val density: Float,
    private val scaledDensity: Float
) {
    fun dp(value: Float): Int = (value * density).roundToInt()

    fun sp(value: Float): Float = value * scaledDensity
}

internal enum class GestureVisualMotionMode {
    ANIMATED,
    STATIC
}

internal object GestureVisualMotionModeResolver {
    fun resolve(animatorsEnabled: Boolean): GestureVisualMotionMode {
        return if (animatorsEnabled) {
            GestureVisualMotionMode.ANIMATED
        } else {
            GestureVisualMotionMode.STATIC
        }
    }
}

internal object GestureVisualMotionPolicy {
    fun animationsEnabled(): Boolean {
        return GestureVisualMotionModeResolver.resolve(ValueAnimator.areAnimatorsEnabled()) ==
            GestureVisualMotionMode.ANIMATED
    }
}
