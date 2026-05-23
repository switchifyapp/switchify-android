package com.enaboapps.switchify.screens.replydrafter

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.enaboapps.switchify.R
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme

/**
 * Visible, foreground screen that drafts replies. Being the top foreground
 * activity is what lets AICore (Gemini Nano) run inference — it refuses when
 * the caller is in the background, so drafting is started from [onResume].
 */
class ReplyDrafterActivity : ComponentActivity() {
    private val viewModel: ReplyDrafterViewModel by viewModels {
        viewModelFactory {
            initializer { ReplyDrafterViewModel(applicationContext) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.observeAsState(ReplyDrafterUiState.Loading)
            val guidance by viewModel.guidance.observeAsState("")

            SwitchifyTheme {
                ReplyDrafterScreen(
                    state = state,
                    guidance = guidance,
                    onSelect = { reply ->
                        viewModel.copyToClipboard(reply)
                        Toast.makeText(
                            this@ReplyDrafterActivity,
                            R.string.reply_drafter_copied,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onGuidanceChange = viewModel::setGuidance,
                    onRegenerate = { viewModel.regenerate() },
                    onRetry = { viewModel.draft() },
                    onClose = { finish() }
                )
            }
        }
    }

    // AICore only runs inference while this activity is the top foreground
    // app, so drafting starts here rather than from the view model's init.
    override fun onResume() {
        super.onResume()
        viewModel.start()
    }
}
