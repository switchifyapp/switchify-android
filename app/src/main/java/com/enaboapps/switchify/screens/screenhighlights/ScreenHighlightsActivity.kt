package com.enaboapps.switchify.screens.screenhighlights

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
 * Visible, foreground screen that extracts Screen Highlights. Being the top
 * foreground activity is what lets AICore (Gemini Nano) run inference — it
 * refuses when the caller is in the background, so extraction is started from
 * [onResume].
 */
class ScreenHighlightsActivity : ComponentActivity() {
    private val viewModel: ScreenHighlightsViewModel by viewModels {
        viewModelFactory {
            initializer { ScreenHighlightsViewModel(applicationContext) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.observeAsState(ScreenHighlightsUiState.Loading)

            SwitchifyTheme {
                ScreenHighlightsScreen(
                    state = state,
                    onSelect = { item ->
                        viewModel.copyToClipboard(item.value)
                        Toast.makeText(
                            this@ScreenHighlightsActivity,
                            R.string.screen_highlights_copied,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onRetry = { viewModel.extract() },
                    onClose = { finish() }
                )
            }
        }
    }

    // AICore only runs inference while this activity is the top foreground
    // app, so extraction starts here rather than from the view model's init.
    override fun onResume() {
        super.onResume()
        viewModel.start()
    }
}
