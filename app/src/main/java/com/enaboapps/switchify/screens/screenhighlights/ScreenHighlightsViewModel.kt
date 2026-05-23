package com.enaboapps.switchify.screens.screenhighlights

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.llm.AiFailure
import com.enaboapps.switchify.service.llm.AiResult
import com.enaboapps.switchify.service.llm.ExtractedItem
import com.enaboapps.switchify.service.llm.OnDeviceAi
import com.enaboapps.switchify.service.llm.ScreenHighlightsScreenshotHolder
import com.enaboapps.switchify.service.llm.ScreenHighlightsTask
import kotlinx.coroutines.launch

sealed interface ScreenHighlightsUiState {
    data object Loading : ScreenHighlightsUiState
    data class Items(val items: List<ExtractedItem>) : ScreenHighlightsUiState
    data object Empty : ScreenHighlightsUiState
    data class Failed(val messageRes: Int, val canRetry: Boolean) : ScreenHighlightsUiState
}

class ScreenHighlightsViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val bitmap: Bitmap? = ScreenHighlightsScreenshotHolder.consume()

    private val _uiState = MutableLiveData<ScreenHighlightsUiState>(ScreenHighlightsUiState.Loading)
    val uiState: LiveData<ScreenHighlightsUiState> = _uiState

    private var hasStarted = false

    /**
     * Start extracting once, when the activity first reaches the foreground.
     * AICore (Gemini Nano) only runs inference while the app is the top
     * foreground app, so this must not run from the view model's init.
     */
    fun start() {
        if (hasStarted) return
        hasStarted = true
        extract()
    }

    fun extract() {
        val image = bitmap
        if (image == null) {
            _uiState.value =
                ScreenHighlightsUiState.Failed(R.string.screen_highlights_llm_failed, canRetry = false)
            return
        }
        _uiState.value = ScreenHighlightsUiState.Loading
        viewModelScope.launch {
            _uiState.value = toUiState(OnDeviceAi.run(appContext, ScreenHighlightsTask, image))
        }
    }

    private fun toUiState(result: AiResult<List<ExtractedItem>>): ScreenHighlightsUiState =
        when (result) {
            is AiResult.Success ->
                if (result.value.isEmpty()) {
                    ScreenHighlightsUiState.Empty
                } else {
                    ScreenHighlightsUiState.Items(result.value)
                }

            is AiResult.Failure -> when (result.reason) {
                AiFailure.NOT_READY ->
                    ScreenHighlightsUiState.Failed(
                        R.string.screen_highlights_model_not_ready,
                        canRetry = false
                    )

                AiFailure.INFERENCE_ERROR ->
                    ScreenHighlightsUiState.Failed(
                        R.string.screen_highlights_llm_failed,
                        canRetry = true
                    )
            }
        }

    fun copyToClipboard(value: String) {
        val clipboard =
            appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("highlight", value))
    }
}
