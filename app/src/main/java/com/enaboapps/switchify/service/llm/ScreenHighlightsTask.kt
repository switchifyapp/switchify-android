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
        You are extracting key information from the screenshot. The user is going
        to tap an item to copy it — so each entry must be a single atomic value,
        not a sentence that contains the value.

        1. Scan the whole screen for individual values worth copying. Each one
           becomes its own entry, even when several appear close together.

        2. Label each value with one of these types, in uppercase. Each example
           shows the on-screen text and the exact line you would write:
           - URL — web links and addresses
             "see https://example.com/page for details" -> URL: https://example.com/page
           - PHONE — phone numbers, in the form shown on screen
             "Call 555-123-4567 anytime" -> PHONE: 555-123-4567
           - EMAIL — email addresses
             "Contact us at support@example.com" -> EMAIL: support@example.com
           - DATE — dates and times
             "Your delivery is on Tue, 12 Mar at 3pm" -> DATE: Tue, 12 Mar at 3pm
           - ADDRESS — street or postal addresses
             "Pick up at 1 Infinite Loop, Cupertino" -> ADDRESS: 1 Infinite Loop, Cupertino
           - OTHER — atomic identifiers worth copying: order numbers, tracking
             codes, one-time passcodes, reference numbers
             "Tracking number: ABC-9876" -> OTHER: ABC-9876

        3. Each line is one atomic value — never a sentence, label, or prose
           that contains the value. Strip surrounding text and punctuation.
           "Tracking: ABC123" becomes OTHER: ABC123, not OTHER: Tracking: ABC123.

        4. Use the value exactly as it appears on screen. Do not reformat,
           expand, or summarise it.

        5. List each value only once. If a value appears twice on the screen,
           include it once. Multiple distinct URLs on one screen are multiple
           URL: lines — pick all of them, not just the first.

        6. Skip generic UI text, button labels, headings, and any prose that
           contains no atomic value to copy.

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
