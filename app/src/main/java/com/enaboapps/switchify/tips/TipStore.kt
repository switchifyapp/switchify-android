package com.enaboapps.switchify.tips

import org.json.JSONArray
import org.json.JSONObject

internal object TipStore {
    const val MAX_TIPS = 10
    private const val KEY_KEY = "key"
    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_BODY = "body"
    private const val KEY_URL = "url"

    fun key(tip: TipMessage): String = (tip.id ?: tip.title).hashCode().toString()

    fun isKey(value: String?): Boolean = value?.toIntOrNull() != null

    fun remember(stored: String?, tip: TipMessage): String {
        val key = key(tip)
        val kept = decode(stored).filter { it.first != key } + (key to tip)
        return JSONArray().apply {
            kept.takeLast(MAX_TIPS).forEach { (tipKey, keptTip) ->
                put(
                    JSONObject()
                        .put(KEY_KEY, tipKey)
                        .putOpt(KEY_ID, keptTip.id)
                        .put(KEY_TITLE, keptTip.title)
                        .put(KEY_BODY, keptTip.body)
                        .putOpt(KEY_URL, keptTip.url)
                )
            }
        }.toString()
    }

    fun find(stored: String?, key: String): TipMessage? {
        return decode(stored).lastOrNull { it.first == key }?.second
    }

    private fun decode(stored: String?): List<Pair<String, TipMessage>> {
        if (stored.isNullOrEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val key = entry.optString(KEY_KEY).takeIf(::isKey) ?: return@mapNotNull null
                val title = entry.optString(KEY_TITLE).trim()
                val body = entry.optString(KEY_BODY).trim()
                if (title.isEmpty() || body.isEmpty()) return@mapNotNull null
                key to TipMessage(
                    id = entry.optString(KEY_ID).takeIf { it.isNotEmpty() },
                    title = title,
                    body = body,
                    url = entry.optString(KEY_URL).takeIf { it.isNotEmpty() && TipMessage.isAllowedUrl(it) }
                )
            }
        }.getOrDefault(emptyList())
    }
}
