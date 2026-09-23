package com.enaboapps.switchify.service.window

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.components.overlayTween
import com.enaboapps.switchify.service.components.rememberOverlayMotionEnabled
import com.enaboapps.switchify.service.scanning.ScanInterval
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanVisualConstants
import com.enaboapps.switchify.service.techniques.nodes.NodeSpeaker
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import com.enaboapps.switchify.utils.Resources
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bottom-of-screen overlay for service messages, drawn with Compose.
 *
 * Visual style mirrors [MenuHighlightHud]: a rounded surface-container card
 * with a severity-tinted accent and icon. Motion uses the shared overlay
 * timings and collapses to instant changes when animations are disabled.
 *
 * Which message is on screen, and in what form, is decided by
 * [ServiceHudMessageController]: a toast shows the instant it is sent, and a
 * status message sits underneath it and collapses to a chip once it has been
 * seen. This class owns the clock, the view, and the drawing.
 *
 * Messages are announced to accessibility services as a live region and,
 * when item-scan speech is on, read aloud through [NodeSpeaker]. A tap on the
 * card, a sideways swipe, or the menu's dismiss entry ends the message.
 *
 * Usage:
 * 1. `setup(applicationContext)` from the accessibility service's `onCreate`.
 * 2. `show(ServiceHudMessage(...))`, or the resource-based convenience
 *    overloads that map the legacy type and time pair onto a duration.
 * 3. `dispose()` from `onDestroy`.
 */
class ServiceMessageHUD private constructor() {
    companion object {
        val instance: ServiceMessageHUD by lazy { ServiceMessageHUD() }
        private const val TAG = "ServiceMessageHUD"
        private val CARD_MAX_WIDTH = 600.dp
        private val CHIP_MAX_WIDTH = 320.dp
        private val CARD_SHAPE = RoundedCornerShape(28.dp)
        private const val COUNTDOWN_IDLE_REFRESH_MS = 1000L
    }

    private var applicationCtx: Context? = null
    private var scanSettings: ScanSettings? = null
    private var composeView: AccessibilityComposeView? = null
    private var attachedTarget: OverlayTarget.Display? = null
    private val handler = Handler(Looper.getMainLooper())
    private val controller = ServiceHudMessageController()
    private val tickRunnable = Runnable { apply(controller.tick(now())) }

    // Compose state. The last shown frame is kept through the exit animation
    // so the card does not blank while fading out; only visibility toggles.
    private val contentState = mutableStateOf(HudFrame.HIDDEN)
    private val visibleState = mutableStateOf(false)

    /** Legacy message kinds, kept for the resource-based overloads. */
    enum class MessageType {
        /** Hides itself after the given [Time]. */
        DISAPPEARING,

        /** Stays until cleared or replaced. */
        PERMANENT
    }

    /** Preset durations for disappearing messages. */
    enum class Time(val milliseconds: Long) {
        SHORT(1500),
        MEDIUM(5000),
        LONG(10000)
    }

    fun setup(appCtx: Context) {
        val app = appCtx.applicationContext
        applicationCtx = app
        scanSettings = ScanSettings(app)
        Log.d(TAG, "ServiceMessageHUD setup")
    }

    /** Shows [message] at once; a toast replaces the current toast, a status replaces the previous status. */
    fun show(message: ServiceHudMessage) {
        if (applicationCtx == null) {
            Log.e(TAG, "ApplicationContext is null, cannot show message. Call setup() first.")
            return
        }
        handler.post {
            ensureComposeViewIsCreated()
            Log.d(TAG, "Showing message: \"${message.text}\" severity=${message.severity} duration=${message.durationMillis}")
            apply(controller.show(message, now()))
            if (message.speak && scanSettings?.isItemScanSpeechEnabled() == true) {
                NodeSpeaker.speakText(message.text)
            }
        }
    }

    fun showMessage(
        messageResId: Int,
        messageType: MessageType,
        time: Time = Time.MEDIUM,
        severity: MessageSeverity = MessageSeverity.Info,
        target: OverlayTarget.Display = OverlayTargets.defaultDisplay(),
        key: String? = null
    ) {
        show(
            ServiceHudMessage(
                text = Resources.getString(messageResId),
                severity = severity,
                durationMillis = ServiceHudMessage.durationFor(messageType, time),
                target = target,
                key = key
            )
        )
    }

    fun showMessage(
        messageResId: Int,
        messageArgs: Array<out Any>,
        messageType: MessageType,
        time: Time = Time.MEDIUM,
        severity: MessageSeverity = MessageSeverity.Info,
        target: OverlayTarget.Display = OverlayTargets.defaultDisplay(),
        key: String? = null
    ) {
        show(
            ServiceHudMessage(
                text = Resources.getString(messageResId, *messageArgs),
                severity = severity,
                durationMillis = ServiceHudMessage.durationFor(messageType, time),
                target = target,
                key = key
            )
        )
    }

    fun showMessageText(
        message: String,
        messageType: MessageType,
        time: Time = Time.MEDIUM,
        severity: MessageSeverity = MessageSeverity.Info,
        target: OverlayTarget.Display = OverlayTargets.defaultDisplay(),
        key: String? = null
    ) {
        show(
            ServiceHudMessage(
                text = message,
                severity = severity,
                durationMillis = ServiceHudMessage.durationFor(messageType, time),
                target = target,
                key = key
            )
        )
    }

    /** Whether a status message is held, whether or not a toast is covering it. Main thread only. */
    fun hasStatus(): Boolean = controller.hasStatus()

    /** Drops the status message, or only the one carrying [key], leaving toasts alone. */
    fun dismissStatus(key: String? = null) {
        handler.post { apply(controller.dismissStatus(now(), key)) }
    }

    /** Removes every message, including any status and queued toasts. */
    fun clearMessage() {
        handler.post { apply(controller.clear(now())) }
    }

    fun dispose() {
        Log.d(TAG, "Disposing ServiceMessageHUD")
        handler.removeCallbacksAndMessages(null)
        controller.clear(now())
        composeView?.let { view ->
            try {
                SwitchifyAccessibilityWindow.instance.removeView(
                    attachedTarget ?: OverlayTargets.defaultDisplay(),
                    view
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove HUD view", e)
            }
        }
        composeView = null
        attachedTarget = null
        contentState.value = HudFrame.HIDDEN
        visibleState.value = false
        scanSettings = null
        applicationCtx = null
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    /** Renders [frame] and schedules the controller's next tick. Main thread only. */
    private fun apply(frame: HudFrame) {
        handler.removeCallbacks(tickRunnable)
        val message = frame.message
        if (message != null) {
            if (!attachIfNeeded(message.target)) return
            contentState.value = frame
        }
        visibleState.value = message != null
        frame.nextTickAt?.let { at ->
            handler.postDelayed(tickRunnable, (at - now()).coerceAtLeast(0L))
        }
    }

    private fun ensureComposeViewIsCreated() {
        val ctx = applicationCtx ?: return
        if (composeView == null) {
            composeView = AccessibilityComposeView(ctx) {
                ServiceMessageUi(
                    frame = contentState.value,
                    isVisible = visibleState.value,
                    onDismiss = { apply(controller.dismiss(now())) }
                )
            }
        }
    }

    /** Attaches the view to [target], moving it if it is on another display. */
    private fun attachIfNeeded(target: OverlayTarget.Display): Boolean {
        val view = composeView ?: return false
        try {
            if (view.parent != null && attachedTarget != target) {
                SwitchifyAccessibilityWindow.instance.removeView(
                    attachedTarget ?: OverlayTargets.defaultDisplay(),
                    view
                )
                attachedTarget = null
            }
            if (view.parent == null) {
                SwitchifyAccessibilityWindow.instance.addViewToBottom(target, view)
                attachedTarget = target
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach HUD view", e)
            return false
        }
    }

    /** What the cross-fade keys on: the countdown is deliberately left out so timer refreshes do not re-animate. */
    private data class ContentKey(
        val text: String,
        val severity: MessageSeverity,
        val form: HudPresentation
    )

    @Composable
    private fun ServiceMessageUi(
        frame: HudFrame,
        isVisible: Boolean,
        onDismiss: () -> Unit
    ) {
        val motion = rememberOverlayMotionEnabled()
        val message = frame.message
        val presentation = frame.presentation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isVisible && message != null && presentation != null,
                enter = fadeIn(overlayTween(ScanVisualConstants.SHOW_DURATION_MS, motion)) +
                    slideInVertically(overlayTween(ScanVisualConstants.SHOW_DURATION_MS, motion)) { it / 4 },
                exit = fadeOut(overlayTween(ScanVisualConstants.HIDE_DURATION_MS, motion))
            ) {
                if (message == null || presentation == null) return@AnimatedVisibility
                SwipeableMessageCard(
                    modifier = Modifier
                        .widthIn(max = CARD_MAX_WIDTH)
                        .fillMaxWidth()
                        .padding(16.dp),
                    message = message,
                    onDismiss = onDismiss
                ) {
                    MessageCard(
                        message = message,
                        presentation = presentation,
                        motionEnabled = motion,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }

    /** Horizontal swipe-to-dismiss wrapper. Kept for carers; switch users use tap or the menu entry. */
    @Composable
    private fun SwipeableMessageCard(
        modifier: Modifier = Modifier,
        message: ServiceHudMessage,
        onDismiss: () -> Unit,
        content: @Composable () -> Unit
    ) {
        val offsetX = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(message.text, message.severity) {
            offsetX.snapTo(0f)
        }

        Box(
            modifier = modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = size.width * 0.4f
                            if (abs(offsetX.value) > threshold) {
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = if (offsetX.value > 0) size.width.toFloat() else -size.width.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    onDismiss()
                                }
                            } else {
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }

    @Composable
    private fun MessageCard(
        message: ServiceHudMessage,
        presentation: HudPresentation,
        motionEnabled: Boolean,
        onDismiss: () -> Unit
    ) {
        val dismissLabel = stringResource(R.string.menu_item_dismiss_message)
        // Cross-fade the whole card when the text, severity, or form changes
        // so the surface stays put and only its content and size move.
        Crossfade(
            targetState = ContentKey(message.text, message.severity, presentation),
            animationSpec = overlayTween(ScanVisualConstants.SHOW_DURATION_MS, motionEnabled),
            label = "ServiceMessageContent"
        ) { key ->
            val widthModifier = when (key.form) {
                HudPresentation.CHIP -> Modifier.widthIn(max = CHIP_MAX_WIDTH)
                else -> Modifier.fillMaxWidth()
            }
            val accent = key.severity.accentColor()
            Card(
                modifier = widthModifier
                    .animateContentSize(overlayTween<IntSize>(ScanVisualConstants.SHOW_DURATION_MS, motionEnabled))
                    .clickable(onClickLabel = dismissLabel, onClick = onDismiss),
                shape = CARD_SHAPE,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column {
                    when (key.form) {
                        HudPresentation.BANNER -> MessageRow(
                            text = key.text,
                            severity = key.severity,
                            accent = accent,
                            iconSize = 24.dp,
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
                            maxLines = 5,
                            verticalPadding = 12.dp,
                            showAccentEdge = true
                        )
                        HudPresentation.TOAST -> MessageRow(
                            text = key.text,
                            severity = key.severity,
                            accent = accent,
                            iconSize = 20.dp,
                            textStyle = MaterialTheme.typography.titleMedium,
                            maxLines = 3,
                            verticalPadding = 8.dp,
                            showAccentEdge = true
                        )
                        HudPresentation.CHIP -> MessageRow(
                            text = key.text,
                            severity = key.severity,
                            accent = accent,
                            iconSize = 20.dp,
                            textStyle = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            verticalPadding = 8.dp,
                            showAccentEdge = false,
                            fillWidth = false
                        )
                    }
                    message.countdown?.let { countdown ->
                        CountdownBar(countdown = countdown, color = accent, motionEnabled = motionEnabled)
                    }
                }
            }
        }
    }

    @Composable
    private fun MessageRow(
        text: String,
        severity: MessageSeverity,
        accent: Color,
        iconSize: Dp,
        textStyle: TextStyle,
        maxLines: Int,
        verticalPadding: Dp,
        showAccentEdge: Boolean,
        fillWidth: Boolean = true
    ) {
        val liveRegion = if (severity == MessageSeverity.Error) LiveRegionMode.Assertive else LiveRegionMode.Polite
        Row(
            modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(IntrinsicSize.Min)
                .padding(horizontal = 16.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showAccentEdge) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accent, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Icon(
                imageVector = severity.icon(),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = accent
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier)
                    .semantics { this.liveRegion = liveRegion },
                style = textStyle,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    /** Thin bar that drains as [countdown] runs out, e.g. the pause timeout. */
    @Composable
    private fun CountdownBar(countdown: ScanInterval, color: Color, motionEnabled: Boolean) {
        var fraction by remember(countdown) {
            mutableFloatStateOf(countdown.remainingFraction(SystemClock.uptimeMillis()))
        }
        LaunchedEffect(countdown, motionEnabled) {
            while (isActive) {
                fraction = countdown.remainingFraction(SystemClock.uptimeMillis())
                if (fraction <= 0f) break
                if (motionEnabled) withFrameMillis { } else delay(COUNTDOWN_IDLE_REFRESH_MS)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                .height(3.dp)
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }

    @Composable
    private fun MessageSeverity.accentColor(): Color = when (this) {
        MessageSeverity.Info -> MaterialTheme.colorScheme.secondary
        MessageSeverity.Success -> SwitchifyTheme.semanticColors.success
        MessageSeverity.Warning -> SwitchifyTheme.semanticColors.warning
        MessageSeverity.Error -> MaterialTheme.colorScheme.error
    }

    private fun MessageSeverity.icon(): ImageVector = when (this) {
        MessageSeverity.Info -> Icons.Filled.Info
        MessageSeverity.Success -> Icons.Filled.CheckCircle
        MessageSeverity.Warning -> Icons.Filled.Warning
        MessageSeverity.Error -> Icons.Filled.Error
    }
}
