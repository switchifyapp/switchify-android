package com.enaboapps.switchify.service.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualMotionPolicy

/**
 * Compose access to the overlay motion policy so every Compose-drawn overlay
 * gates its animation in the same place as the View-based ones.
 */
@Composable
fun rememberOverlayMotionEnabled(): Boolean = remember { GestureVisualMotionPolicy.animationsEnabled() }

/**
 * A tween on the shared overlay easing curve that collapses to an instant
 * change when motion is disabled.
 */
fun <T> overlayTween(durationMillis: Long, motionEnabled: Boolean): TweenSpec<T> = tween(
    durationMillis = if (motionEnabled) durationMillis.toInt() else 0,
    easing = FastOutSlowInEasing
)
