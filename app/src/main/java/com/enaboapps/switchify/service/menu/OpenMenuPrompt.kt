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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

/**
 * This class manages the prompt to ask the user if they want to open the menu.
 */
class OpenMenuPrompt {
    private var menuPromptView: AccessibilityComposeView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isShowing = false

    companion object {
        val instance: OpenMenuPrompt by lazy {
            OpenMenuPrompt()
        }

        private const val TAG = "OpenMenuPrompt"
    }

    fun show(context: Context) {
        if (isShowing) return

        mainHandler.post {
            createPromptView(context)
            menuPromptView?.let { SwitchifyAccessibilityWindow.instance.addViewToCenter(it) }
            isShowing = true
        }
    }

    fun hide() {
        if (!isShowing) return

        mainHandler.post {
            menuPromptView?.let { view ->
                SwitchifyAccessibilityWindow.instance.removeView(view)
                isShowing = false
                menuPromptView = null
            }
        }
    }

    @Composable
    private fun PromptView() {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .height(250.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.menu_prompt_press_to_open),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    private fun createPromptView(context: Context) {
        menuPromptView = AccessibilityComposeView(context) {
            SwitchifyTheme {
                PromptView()
            }
        }
    }
} 