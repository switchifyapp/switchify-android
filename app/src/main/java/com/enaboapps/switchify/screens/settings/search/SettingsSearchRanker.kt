package com.enaboapps.switchify.screens.settings.search

import java.text.Normalizer

data class SettingsSearchItem(
    val entry: SettingsSearchEntry,
    val title: String,
    val summary: String?,
    val explanation: String?,
    val path: List<String>
)

object SettingsSearchRanker {

    private const val TITLE_PREFIX = 100
    private const val TITLE_WORD = 80
    private const val TITLE_CONTAINS = 60
    private const val PATH_CONTAINS = 30
    private const val SUMMARY_CONTAINS = 20
    private const val EXPLANATION_CONTAINS = 10

    private val diacritics = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    fun rank(query: String, items: List<SettingsSearchItem>): List<SettingsSearchItem> {
        val tokens = normalise(query).split(whitespace).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return items
            .mapNotNull { item -> score(tokens, item)?.let { item to it } }
            .sortedWith(compareByDescending<Pair<SettingsSearchItem, Int>> { it.second }.thenBy { it.first.title })
            .map { it.first }
    }

    private fun score(tokens: List<String>, item: SettingsSearchItem): Int? {
        val title = normalise(item.title)
        val path = normalise(item.path.joinToString(" "))
        val summary = item.summary?.let(::normalise).orEmpty()
        val explanation = item.explanation?.let(::normalise).orEmpty()
        var total = 0
        for (token in tokens) {
            val tokenScore = when {
                title.startsWith(token) -> TITLE_PREFIX
                title.contains(" $token") -> TITLE_WORD
                title.contains(token) -> TITLE_CONTAINS
                path.contains(token) -> PATH_CONTAINS
                summary.contains(token) -> SUMMARY_CONTAINS
                explanation.contains(token) -> EXPLANATION_CONTAINS
                else -> return null
            }
            total += tokenScore
        }
        return total
    }

    fun normalise(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase()
            .trim()
}
