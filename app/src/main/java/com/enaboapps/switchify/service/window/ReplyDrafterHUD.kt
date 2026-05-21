package com.enaboapps.switchify.service.window

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService

/**
 * Centre-of-screen overlay that lists AI-suggested replies. Selecting a
 * suggestion inserts it into the focused editable field. Lifecycle: `setup` is
 * called once from the accessibility service, `dispose` on service destroy; the
 * view is attached only while suggestions are showing.
 */
class ReplyDrafterHUD private constructor() {
    companion object {
        val instance: ReplyDrafterHUD by lazy { ReplyDrafterHUD() }
        private const val TAG = "ReplyDrafterHUD"
        private const val ANIMATION_OUT_DELAY_MS = 400L
    }

    private var accessibilityService: SwitchifyAccessibilityService? = null
    private var composeView: AccessibilityComposeView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val suggestions = mutableStateListOf<String>()
    private val visibleState = mutableStateOf(false)

    fun setup(service: SwitchifyAccessibilityService) {
        this.accessibilityService = service
    }

    fun showSuggestions(newSuggestions: List<String>) {
        val service = accessibilityService ?: return
        handler.post {
            ensureComposeViewIsCreated(service)
            attachIfNeeded()
            suggestions.clear()
            suggestions.addAll(newSuggestions)
            visibleState.value = true
        }
    }

    fun hide() {
        handler.post {
            visibleState.value = false
            handler.postDelayed({
                if (!visibleState.value) {
                    composeView?.let { SwitchifyAccessibilityWindow.instance.removeView(it) }
                }
            }, ANIMATION_OUT_DELAY_MS)
        }
    }

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        composeView?.let { view ->
            try {
                SwitchifyAccessibilityWindow.instance.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove HUD view", e)
            }
        }
        composeView = null
        suggestions.clear()
        visibleState.value = false
        accessibilityService = null
    }

    private fun ensureComposeViewIsCreated(service: SwitchifyAccessibilityService) {
        if (composeView == null) {
            composeView = AccessibilityComposeView(service.applicationContext) {
                ReplyDrafterUi(
                    suggestions = suggestions,
                    isVisible = visibleState.value,
                    onSuggestionSelected = { suggestion ->
                        insertText(suggestion)
                        hide()
                    },
                    onDismiss = { hide() }
                )
            }
        }
    }

    private fun attachIfNeeded() {
        val view = composeView ?: return
        if (view.parent == null) {
            try {
                SwitchifyAccessibilityWindow.instance.addViewToCenter(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach HUD view", e)
            }
        }
    }

    @Composable
    private fun ReplyDrafterUi(
        suggestions: List<String>,
        isVisible: Boolean,
        onSuggestionSelected: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 520.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.reply_drafter_suggestions_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.reply_drafter_cancel)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (suggestions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.reply_drafter_no_suggestions),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                suggestions.forEach { suggestion ->
                                    SuggestionItem(
                                        text = suggestion,
                                        onClick = { onSuggestionSelected(suggestion) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SuggestionItem(text: String, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    private fun insertText(text: String) {
        val service = accessibilityService ?: return
        val focused = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            setNodeText(focused, text)
            return
        }
        val root = service.rootInActiveWindow
        if (root == null || !insertIntoFirstEditable(root, text)) {
            ServiceMessageHUD.instance.showMessage(
                R.string.reply_drafter_no_text_field,
                ServiceMessageHUD.MessageType.DISAPPEARING,
                ServiceMessageHUD.Time.MEDIUM,
                MessageSeverity.Warning
            )
        }
    }

    private fun insertIntoFirstEditable(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isEditable) {
            setNodeText(node, text)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (insertIntoFirstEditable(child, text)) return true
        }
        return false
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
