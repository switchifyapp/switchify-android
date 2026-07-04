package com.enaboapps.switchify.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Spring-driven press scale used across the app (matches the PC control tiles).
 * Attach the same [interactionSource] to the clickable that triggers the press.
 */
@Composable
fun Modifier.springPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.98f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Spring-animated container color that shifts to [pressedColor] while pressed.
 * [selected] wins over the idle color so selection states animate the same way.
 */
@Composable
fun animatedPressContainerColor(
    interactionSource: InteractionSource,
    idleColor: Color,
    pressedColor: Color,
    selected: Boolean = false,
    selectedColor: Color = pressedColor
): Color {
    val pressed by interactionSource.collectIsPressedAsState()
    val color by animateColorAsState(
        targetValue = when {
            pressed -> pressedColor
            selected -> selectedColor
            else -> idleColor
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressContainerColor"
    )
    return color
}
