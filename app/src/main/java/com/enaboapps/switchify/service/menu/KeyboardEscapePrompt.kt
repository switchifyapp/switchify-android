package com.enaboapps.switchify.service.menu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.components.overlayTween
import com.enaboapps.switchify.service.components.rememberOverlayMotionEnabled
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanVisualConstants
import com.enaboapps.switchify.service.techniques.nodes.NodeSpeaker
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets
import com.enaboapps.switchify.utils.Resources

/**
 * Card shown during the keyboard scan's cycle break, asking whether the user
 * wants to leave the keyboard. It docks just above the keys so the keyboard
 * and the dashed escape highlight stay visible, and follows the HUD's visual
 * language and motion policy. When item-scan speech is on the prompt is also
 * read aloud.
 */
class KeyboardEscapePrompt {
    private var promptView: AccessibilityComposeView? = null
    private var attachedTarget: OverlayTarget.Display? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val visibleState = mutableStateOf(false)
    private var showGeneration = 0L

    companion object {
        val instance: KeyboardEscapePrompt by lazy { KeyboardEscapePrompt() }
        private const val TAG = "KeyboardEscapePrompt"

        /** Room for the card plus its padding above the keys, with space for wrapped body text. */
        private const val BAND_HEIGHT_DP = 120
        private val CARD_MAX_WIDTH = 600.dp
        private val CARD_SHAPE = RoundedCornerShape(28.dp)
        private const val EXIT_SETTLE_MS = 40L
    }

    fun show(
        context: Context,
        target: OverlayTarget.Display = OverlayTargets.defaultDisplay()
    ) {
        val promptTarget = target.copy(forceSurface = true)
        mainHandler.post {
            val generation = ++showGeneration
            if (promptView == null) {
                promptView = AccessibilityComposeView(context) {
                    PromptView(isVisible = visibleState.value)
                }
            }
            val view = promptView ?: return@post
            if (view.parent != null && attachedTarget != promptTarget) {
                detachNow()
            }
            if (view.parent == null) {
                attach(context, view, promptTarget)
            }
            if (!visibleState.value) {
                visibleState.value = true
                announce(context)
            }
            Log.d(TAG, "Escape prompt shown (generation $generation)")
        }
    }

    fun hide() {
        mainHandler.post {
            if (!visibleState.value) return@post
            visibleState.value = false
            val generation = showGeneration
            mainHandler.postDelayed({
                if (generation == showGeneration && !visibleState.value) detachNow()
            }, ScanVisualConstants.HIDE_DURATION_MS + EXIT_SETTLE_MS)
        }
    }

    private fun attach(context: Context, view: AccessibilityComposeView, target: OverlayTarget.Display) {
        val bounds = KeyboardManager.keyboardState.value.keyboardBounds
        val placement = KeyboardEscapePromptPlacement.forKeyboard(
            keyboardLeft = bounds?.left,
            keyboardTop = bounds?.top,
            keyboardWidth = bounds?.width(),
            keyboardHeight = bounds?.height(),
            bandHeight = ScreenUtils.dpToPx(context, BAND_HEIGHT_DP)
        )
        try {
            when (placement) {
                is KeyboardEscapePromptPlacement.Docked -> SwitchifyAccessibilityWindow.instance.addView(
                    target, view, placement.x, placement.y, placement.width, placement.height
                )
                KeyboardEscapePromptPlacement.Centered ->
                    SwitchifyAccessibilityWindow.instance.addViewToCenter(target, view)
            }
            attachedTarget = target
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach escape prompt", e)
        }
    }

    private fun detachNow() {
        val view = promptView ?: return
        try {
            SwitchifyAccessibilityWindow.instance.removeView(
                attachedTarget ?: OverlayTargets.defaultDisplay(),
                view
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove escape prompt", e)
        }
        promptView = null
        attachedTarget = null
    }

    private fun announce(context: Context) {
        if (!ScanSettings(context).isItemScanSpeechEnabled()) return
        NodeSpeaker.speakText(
            Resources.getString(R.string.keyboard_escape_prompt_title) + ". " +
                Resources.getString(R.string.keyboard_escape_prompt_body)
        )
    }

    @Composable
    private fun PromptView(isVisible: Boolean) {
        val keyboardState by KeyboardManager.keyboardState.collectAsState()
        val motion = rememberOverlayMotionEnabled()
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isVisible && keyboardState.shouldShowEscapePrompt,
                enter = fadeIn(overlayTween(ScanVisualConstants.SHOW_DURATION_MS, motion)) +
                    slideInVertically(overlayTween(ScanVisualConstants.SHOW_DURATION_MS, motion)) { it / 4 },
                exit = fadeOut(overlayTween(ScanVisualConstants.HIDE_DURATION_MS, motion))
            ) {
                PromptCard()
            }
        }
    }

    @Composable
    private fun PromptCard() {
        val accent = MaterialTheme.colorScheme.secondary
        Card(
            modifier = Modifier
                .widthIn(max = CARD_MAX_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = CARD_SHAPE,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = accent
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Text(
                        text = stringResource(R.string.keyboard_escape_prompt_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.keyboard_escape_prompt_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
