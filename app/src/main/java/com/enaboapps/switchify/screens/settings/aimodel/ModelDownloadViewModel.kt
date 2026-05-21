package com.enaboapps.switchify.screens.settings.aimodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.work.WorkInfo
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.llm.model.ModelDownloadManager
import com.enaboapps.switchify.service.llm.model.ModelManager

sealed interface ModelDownloadUiState {
    data object NotDownloaded : ModelDownloadUiState
    data object Ready : ModelDownloadUiState
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadUiState
    data object Failed : ModelDownloadUiState
}

class ModelDownloadViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val modelManager = ModelManager(appContext)
    private val preferenceManager = PreferenceManager(appContext)

    private val workInfos = ModelDownloadManager.getWorkInfoLiveData(appContext)
    private val refreshTrigger = MutableLiveData(Unit)

    val uiState: LiveData<ModelDownloadUiState> =
        MediatorLiveData<ModelDownloadUiState>().apply {
            val update = { value = deriveState(workInfos.value.orEmpty()) }
            addSource(workInfos) { update() }
            addSource(refreshTrigger) { update() }
        }

    fun hasEnoughFreeSpace(): Boolean = modelManager.hasEnoughFreeSpace()

    fun isTermsAccepted(): Boolean = preferenceManager.getBooleanValue(
        PreferenceManager.PREFERENCE_KEY_GEMMA_TERMS_ACCEPTED
    )

    fun startDownload() {
        if (!isTermsAccepted()) return
        ModelDownloadManager.enqueue(appContext)
    }

    fun cancelDownload() {
        ModelDownloadManager.cancel(appContext)
    }

    fun deleteModel() {
        modelManager.deleteModel()
        refreshTrigger.value = Unit
    }

    private fun deriveState(infos: List<WorkInfo>): ModelDownloadUiState {
        if (modelManager.isModelReady()) return ModelDownloadUiState.Ready
        val latest = infos.lastOrNull()
        return when (latest?.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING,
            WorkInfo.State.BLOCKED -> ModelDownloadUiState.Downloading(
                latest.progress.getLong(ModelDownloadManager.KEY_PROGRESS_BYTES, 0L),
                latest.progress.getLong(ModelDownloadManager.KEY_PROGRESS_TOTAL, 0L)
            )

            WorkInfo.State.FAILED -> ModelDownloadUiState.Failed
            else -> ModelDownloadUiState.NotDownloaded
        }
    }
}
