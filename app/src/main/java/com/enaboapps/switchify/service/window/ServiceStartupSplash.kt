package com.enaboapps.switchify.service.window

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.utils.Resources

class ServiceStartupSplash private constructor() {
    companion object {
        val instance: ServiceStartupSplash by lazy { ServiceStartupSplash() }
        private const val TAG = "ServiceStartupSplash"
        private const val DISPLAY_MS = 1600L
        private const val EXIT_MS = 220L
    }

    private var applicationCtx: Context? = null
    private var composeView: AccessibilityComposeView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val visibleState = mutableStateOf(false)

    fun setup(appCtx: Context) {
        applicationCtx = appCtx.applicationContext
    }

    fun show() {
        handler.post {
            val context = applicationCtx ?: run {
                Log.e(TAG, "ApplicationContext is null. Call setup() first.")
                return@post
            }

            handler.removeCallbacksAndMessages(null)
            ensureComposeViewIsCreated(context)
            attachIfNeeded()
            visibleState.value = true
            handler.postDelayed({ hide() }, DISPLAY_MS)
        }
    }

    fun hide() {
        handler.post {
            if (!visibleState.value && composeView == null) return@post
            visibleState.value = false
            handler.postDelayed({
                if (!visibleState.value) {
                    composeView?.let { view ->
                        try {
                            SwitchifyAccessibilityWindow.instance.removeView(view)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to remove splash view", e)
                        }
                    }
                }
            }, EXIT_MS)
        }
    }

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        composeView?.let { view ->
            try {
                SwitchifyAccessibilityWindow.instance.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispose splash view", e)
            }
        }
        composeView = null
        visibleState.value = false
        applicationCtx = null
    }

    private fun ensureComposeViewIsCreated(context: Context) {
        if (composeView == null) {
            composeView = AccessibilityComposeView(context) {
                ServiceStartupSplashUi(isVisible = visibleState.value)
            }
        }
    }

    private fun attachIfNeeded() {
        val view = composeView ?: return
        if (view.parent == null) {
            try {
                SwitchifyAccessibilityWindow.instance.addViewToCenter(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach splash view", e)
            }
        }
    }

    @Composable
    private fun ServiceStartupSplashUi(isVisible: Boolean) {
        val colors = ServiceStartupSplashColors.current()
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(180)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(EXIT_MS.toInt())) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(EXIT_MS.toInt()))
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = colors.container,
                    contentColor = colors.content
                ),
                border = BorderStroke(1.dp, colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PulseIndicator()
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = Resources.getString(R.string.service_startup_splash_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = colors.content
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Resources.getString(R.string.service_startup_splash_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = colors.secondaryContent
                    )
                }
            }
        }
    }

    @Composable
    private fun PulseIndicator() {
        val colors = ServiceStartupSplashColors.current()
        val transition = rememberInfiniteTransition(label = "ServiceStartupSplashPulse")
        val pulse = transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ServiceStartupSplashPulseScale"
        )
        val alpha = transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ServiceStartupSplashPulseAlpha"
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val dotScale = if (index == 1) pulse.value else 0.9f + ((pulse.value - 0.82f) * 0.35f)
                Box(
                    modifier = Modifier
                        .size(if (index == 1) 12.dp else 9.dp)
                        .scale(dotScale)
                        .alpha(if (index == 1) alpha.value else 0.7f)
                        .background(colors.indicator, CircleShape)
                )
                if (index == 0) {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }
    }

    private data class ServiceStartupSplashColors(
        val container: Color,
        val border: Color,
        val content: Color,
        val secondaryContent: Color,
        val indicator: Color
    ) {
        companion object {
            @Composable
            fun current(): ServiceStartupSplashColors {
                return if (isSystemInDarkTheme()) {
                    ServiceStartupSplashColors(
                        container = Color(0xFF211F24).copy(alpha = 0.97f),
                        border = Color(0xFF4F4852).copy(alpha = 0.9f),
                        content = Color(0xFFF4EFF4),
                        secondaryContent = Color(0xFFD7D0D8),
                        indicator = MaterialTheme.colorScheme.primary
                    )
                } else {
                    ServiceStartupSplashColors(
                        container = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f),
                        content = MaterialTheme.colorScheme.onSurface,
                        secondaryContent = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicator = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
