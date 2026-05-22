package com.enaboapps.switchify.screens.replydrafter

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme

/**
 * Visible, foreground screen that drafts replies. Being the top foreground
 * activity is what lets AICore (Gemini Nano) run inference — it refuses when
 * the caller is in the background.
 */
class ReplyDrafterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val viewModel: ReplyDrafterViewModel = viewModel { ReplyDrafterViewModel(context) }
            val state by viewModel.uiState.observeAsState(ReplyDrafterUiState.Loading)

            SwitchifyTheme {
                ReplyDrafterScreen(
                    state = state,
                    onSelect = { reply ->
                        viewModel.copyToClipboard(reply)
                        Toast.makeText(
                            this@ReplyDrafterActivity,
                            R.string.reply_drafter_copied,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onRetry = { viewModel.draft() },
                    onClose = { finish() }
                )
            }
        }
    }
}
