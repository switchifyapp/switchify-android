package com.enaboapps.switchify.service.llm.model

object ReplyDrafterModelConfig {
    // The hosted .task model URL. Gemma models require license acceptance, so
    // this must point at a file the maintainer self-hosts. The in-app download
    // is disabled until this is set.
    const val MODEL_URL = "https://tbicuaixtiyyfzhgpklr.supabase.co/storage/v1/object/public/ai-models/gemma-3n-E2B-it-int4.task"

    const val MODEL_FILE_NAME = "gemma-3n-E2B-it-int4.task"
    const val MODEL_SUBDIR = "llm"

    // The hosted artifact's exact byte size. Enables download progress, the
    // free-space precheck, and the integrity check. Checks that depend on it
    // are skipped while it is 0.
    const val EXPECTED_SIZE_BYTES = 3136226711L

    // Optional lowercase-hex SHA-256 of the hosted artifact. Integrity check is
    // skipped while it is blank.
    const val EXPECTED_SHA256 = ""

    // Google's official Gemma legal documents, shown on the terms screen.
    const val GEMMA_TERMS_URL = "https://ai.google.dev/gemma/terms"
    const val GEMMA_USE_POLICY_URL = "https://ai.google.dev/gemma/prohibited_use_policy"

    fun isDownloadConfigured(): Boolean = MODEL_URL.isNotBlank()
}
