package com.enaboapps.switchify.service.llm

enum class HighlightType {
    URL,
    PHONE,
    EMAIL,
    DATE,
    ADDRESS,
    OTHER;

    companion object {
        fun fromLabelOrOther(raw: String): HighlightType {
            val upper = raw.trim().uppercase()
            return entries.firstOrNull { it.name == upper } ?: OTHER
        }
    }
}

data class ExtractedItem(val type: HighlightType, val value: String)

/**
 * The Screen Highlights [AiTask]: the prompt that asks an on-device model to
 * pull every actionable item out of a screenshot, and the parser that turns
 * the model's text output into a typed list.
 */
object ScreenHighlightsTask : AiTask<List<ExtractedItem>> {
    private const val MAX_ITEMS = 15

    private const val ITEMS_OPEN = "<items>"
    private const val ITEMS_CLOSE = "</items>"

    override val prompt: String = """
        You are extracting actionable information from the screenshot. Think about
        what someone using a switch-access input device would want to copy or open
        without navigating into the underlying app, then write only the list.

        1. Look across the whole screen — visible text, fields, buttons, anywhere
           a useful value might appear.
        2. Pull out each piece of useful information you see and label it with one
           of these types, in uppercase:
           - URL: web links and addresses
           - PHONE: phone numbers, in the form shown on screen
           - EMAIL: email addresses
           - DATE: dates and times
           - ADDRESS: street or postal addresses
           - OTHER: anything else worth copying — order numbers, tracking codes,
             one-time passcodes, reference numbers
        3. Use the value exactly as it appears on screen. Do not reformat, expand,
           or summarise it.
        4. Skip generic UI text, button labels, headings, and anything that would
           not be useful to copy.
        5. List each item only once. If a value appears twice on the screen,
           include it once.

        Format the answer as exactly this, and nothing else:
        $ITEMS_OPEN
        TYPE: value
        TYPE: value
        $ITEMS_CLOSE
        One item per line, with the TYPE in uppercase followed by a colon and the
        value. Write nothing outside the tags: no numbering, labels, preamble, or
        other text. If the screen has no useful information, write the tags with
        nothing between them.
    """.trimIndent()

    private val listMarker = Regex("^(\\d+[.)]|[-*•])\\s*")

    /**
     * Extract the items from the model output. Small models leak preamble and
     * trailing commentary despite the prompt, so only the text between the
     * item tags is kept; everything outside is discarded. When the tags are
     * absent the result is empty — a free-form preamble is not worth
     * misclassifying.
     */
    override fun parse(raw: String): List<ExtractedItem> {
        val region = raw
            .substringAfterLast(ITEMS_OPEN, "")
            .substringBefore(ITEMS_CLOSE)
        return region.lines()
            .mapNotNull { line ->
                val cleaned = line.trim().replace(listMarker, "").trim()
                if (cleaned.isEmpty() || !cleaned.contains(':')) return@mapNotNull null
                val type = HighlightType.fromLabelOrOther(cleaned.substringBefore(':'))
                val value = cleaned.substringAfter(':')
                    .trim()
                    .removeSurrounding("\"")
                    .trim()
                if (value.isEmpty()) null else ExtractedItem(type, value)
            }
            .distinct()
            .take(MAX_ITEMS)
    }
}
