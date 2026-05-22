package com.enaboapps.switchify.service.llm.model

import com.enaboapps.switchify.BuildConfig

object AiModelConfig {
    // The hosted .litertlm model URL, injected at build time from the AI_MODEL_URL
    // environment variable (or aiModel.url in local.properties); the build
    // fails if neither is set. Gemma models require license acceptance, so this
    // must point at a file the maintainer self-hosts.
    val MODEL_URL: String = BuildConfig.AI_MODEL_URL

    const val MODEL_FILE_NAME = "gemma-3n-E2B-it-int4.litertlm"
    const val MODEL_SUBDIR = "llm"

    // The hosted artifact's exact byte size. Enables download progress, the
    // free-space precheck, and the integrity check. Checks that depend on it
    // are skipped while it is 0.
    const val EXPECTED_SIZE_BYTES = 0L

    // Optional lowercase-hex SHA-256 of the hosted artifact. Integrity check is
    // skipped while it is blank.
    const val EXPECTED_SHA256 = ""

    // Google's official Gemma legal documents, shown on the terms screen.
    const val GEMMA_TERMS_URL = "https://ai.google.dev/gemma/terms"
    const val GEMMA_USE_POLICY_URL = "https://ai.google.dev/gemma/prohibited_use_policy"
}
