package com.enaboapps.switchify.service.window

import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService

/**
 * ReplyDrafterHUD displays suggested replies in an overlay.
 */
class ReplyDrafterHUD private constructor() {
    companion object {
        val instance: ReplyDrafterHUD by lazy { ReplyDrafterHUD() }
        private const val TAG = "ReplyDrafterHUD"
    }

    private var accessibilityService: SwitchifyAccessibilityService? = null
    private var drafterComposeView: AccessibilityComposeView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val suggestions = mutableStateListOf<String>()
    private val isVisible = mutableStateOf(false)

    /**
     * Sets up the HUD with the accessibility service context
     */
    fun setup(service: SwitchifyAccessibilityService) {
        this.accessibilityService = service
        Log.d(TAG, "ReplyDrafterHUD setup")
    }

    /**
     * Shows the suggested replies
     */
    fun showSuggestions(newSuggestions: List<String>) {
        val service = accessibilityService ?: return
        
        handler.post {
            suggestions.clear()
            suggestions.addAll(newSuggestions)
            
            ensureComposeViewCreated(service)
            
            drafterComposeView?.let { view ->
                if (view.parent == null) {
                    SwitchifyAccessibilityWindow.instance.addViewToCenter(view)
                }
            }
            
            isVisible.value = true
        }
    }

    /**
     * Hides the HUD
     */
    fun hide() {
        isVisible.value = false
        // Delay removal to allow animation
        handler.postDelayed({
            if (!isVisible.value) {
                drafterComposeView?.let { view ->
                    SwitchifyAccessibilityWindow.instance.removeView(view)
                }
            }
        }, 500L)
    }

    private fun ensureComposeViewCreated(service: SwitchifyAccessibilityService) {
        if (drafterComposeView == null) {
            drafterComposeView = AccessibilityComposeView(service.applicationContext) {
                ReplyDrafterUi(
                    suggestions = suggestions,
                    visible = isVisible.value,
                    onSuggestionClick = { suggestion ->
                        insertText(suggestion)
                        hide()
                    },
                    onClose = { hide() }
                )
            }
        }
    }

    @Composable
    private fun ReplyDrafterUi(
        suggestions: List<String>,
        visible: Boolean,
        onSuggestionClick: (String) -> Unit,
        onClose: () -> Unit
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QuestionAnswer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Suggested Replies",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (suggestions.isEmpty()) {
                        Text(
                            text = "No suggestions found.",
                            modifier = Modifier.padding(vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(suggestions) { suggestion ->
                                SuggestionItem(
                                    text = suggestion,
                                    onClick = { onSuggestionClick(suggestion) }
                                )
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
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    private fun insertText(text: String) {
        val service = accessibilityService ?: return
        val rootNode = service.rootInActiveWindow ?: return
        
        // Find focused input field
        val focusedNode = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        
        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "Inserted text into focused node")
        } else {
            Log.w(TAG, "No focused editable node found")
            // Fallback: try to find any editable node if focus search failed
            findAndInsertIntoFirstEditable(rootNode, text)
        }
    }

    private fun findAndInsertIntoFirstEditable(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isEditable) {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            return true
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndInsertIntoFirstEditable(child, text)) return true
        }
        
        return false
    }

    fun dispose() {
        drafterComposeView?.let { view ->
            SwitchifyAccessibilityWindow.instance.removeView(view)
        }
        drafterComposeView = null
        accessibilityService = null
    }
}
