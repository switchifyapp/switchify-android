package com.enaboapps.switchify.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

internal class CrashUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return when (CrashReporter.uploadQueuedCrashes(applicationContext)) {
            CrashUploadOutcome.Success -> Result.success()
            CrashUploadOutcome.Retry -> Result.retry()
        }
    }
}
