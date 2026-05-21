package com.enaboapps.switchify.service.llm.model

object ReplyDrafterModelConfig {
    // The hosted .task model URL. Gemma models require license acceptance, so
    // this must point at a file the maintainer self-hosts. The in-app download
    // is disabled until this is set.
    const val MODEL_URL = ""

    const val MODEL_FILE_NAME = "gemma-3n-E2B-it-int4.task"
    const val MODEL_SUBDIR = "llm"

    // The hosted artifact's exact byte size. Enables download progress, the
    // free-space precheck, and the integrity check. Checks that depend on it
    // are skipped while it is 0.
    const val EXPECTED_SIZE_BYTES = 0L

    // Optional lowercase-hex SHA-256 of the hosted artifact. Integrity check is
    // skipped while it is blank.
    const val EXPECTED_SHA256 = ""

    fun isDownloadConfigured(): Boolean = MODEL_URL.isNotBlank()
}
