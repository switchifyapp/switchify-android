package com.enaboapps.switchify.service.menu

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.keyboard.KeyboardManager
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import com.enaboapps.switchify.theme.Dimens

/**
 * This class manages the prompt to ask the user if they want to open the menu or escape from keyboard.
 */
class KeyboardEscapePrompt {
    private var promptView: AccessibilityComposeView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isShowing = false

    companion object {
        val instance: KeyboardEscapePrompt by lazy {
            KeyboardEscapePrompt()
        }

        private const val TAG = "KeyboardEscapePrompt"
    }

    fun show(context: Context) {
        if (isShowing) return

        mainHandler.post {
            createPromptView(context)
            promptView?.let { SwitchifyAccessibilityWindow.instance.addViewToCenter(it) }
            isShowing = true
        }
    }

    fun hide() {
        if (!isShowing) return

        mainHandler.post {
            promptView?.let { view ->
                SwitchifyAccessibilityWindow.instance.removeView(view)
                isShowing = false
                promptView = null
            }
        }
    }

    @Composable
    private fun PromptView() {
        // Reactively observe keyboard state
        val keyboardState by KeyboardManager.keyboardState.collectAsState()

        // Only show prompt when appropriate
        if (keyboardState.shouldShowEscapePrompt) {
            Surface(
                modifier = Modifier
                    .padding(Dimens.spaceM)
                    .height(250.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .padding(Dimens.spaceL),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = Dimens.spaceM),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.keyboard_escape_prompt_press_to_escape),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(Dimens.spaceXs)
                        )
                    }
                }
            }
        }
    }

    private fun createPromptView(context: Context) {
        promptView = AccessibilityComposeView(context) {
            PromptView()
        }
    }
} 
