package com.enaboapps.switchify.tips

import java.net.URI

data class TipMessage(
    val id: String?,
    val title: String,
    val body: String,
    val url: String?
) {
    companion object {
        private const val KEY_ID = "tip_id"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_URL = "url"
        private const val ALLOWED_DOMAIN = "theswitchify.app"

        fun from(data: Map<String, String>): TipMessage? {
            val title = data[KEY_TITLE]?.trim().orEmpty()
            val body = data[KEY_BODY]?.trim().orEmpty()
            if (title.isEmpty() || body.isEmpty()) return null
            return TipMessage(
                id = data[KEY_ID]?.trim()?.takeIf { it.isNotEmpty() },
                title = title,
                body = body,
                url = data[KEY_URL]?.trim()?.takeIf(::isAllowedUrl)
            )
        }

        internal fun isAllowedUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme.equals("https", ignoreCase = true) &&
                    uri.userInfo == null &&
                    (host == ALLOWED_DOMAIN || host.endsWith(".$ALLOWED_DOMAIN"))
        }
    }
}
