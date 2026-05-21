package com.enaboapps.switchify.service.llm.model

import android.content.Context
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import java.io.File

class ModelManager(context: Context) {

    private val appContext = context.applicationContext
    private val preferenceManager = PreferenceManager(appContext)

    fun getModelDir(): File = File(appContext.filesDir, ReplyDrafterModelConfig.MODEL_SUBDIR)

    fun getModelFile(): File = File(getModelDir(), ReplyDrafterModelConfig.MODEL_FILE_NAME)

    fun getPartFile(): File =
        File(getModelDir(), ReplyDrafterModelConfig.MODEL_FILE_NAME + ".part")

    fun ensureModelDir() {
        getModelDir().mkdirs()
    }

    fun isModelReady(): Boolean {
        val downloaded = preferenceManager.getBooleanValue(
            PreferenceManager.PREFERENCE_KEY_REPLY_DRAFTER_MODEL_DOWNLOADED
        )
        if (!downloaded) return false
        val file = getModelFile()
        if (!file.exists() || file.length() == 0L) return false
        val expected = ReplyDrafterModelConfig.EXPECTED_SIZE_BYTES
        return expected <= 0L || file.length() == expected
    }

    fun getModelFileIfReady(): File? = getModelFile().takeIf { isModelReady() }

    fun markModelReady() {
        preferenceManager.setStringValue(
            PreferenceManager.PREFERENCE_KEY_REPLY_DRAFTER_MODEL_PATH,
            getModelFile().absolutePath
        )
        preferenceManager.setBooleanValue(
            PreferenceManager.PREFERENCE_KEY_REPLY_DRAFTER_MODEL_DOWNLOADED,
            true
        )
    }

    fun clearModelState() {
        preferenceManager.setBooleanValue(
            PreferenceManager.PREFERENCE_KEY_REPLY_DRAFTER_MODEL_DOWNLOADED,
            false
        )
        preferenceManager.setStringValue(
            PreferenceManager.PREFERENCE_KEY_REPLY_DRAFTER_MODEL_PATH,
            ""
        )
    }

    fun deleteModel() {
        getModelFile().delete()
        getPartFile().delete()
        clearModelState()
    }

    fun hasEnoughFreeSpace(): Boolean {
        val required = ReplyDrafterModelConfig.EXPECTED_SIZE_BYTES
        if (required <= 0L) return true
        return appContext.filesDir.usableSpace > required + FREE_SPACE_HEADROOM_BYTES
    }

    companion object {
        private const val FREE_SPACE_HEADROOM_BYTES = 250L * 1024 * 1024
    }
}
