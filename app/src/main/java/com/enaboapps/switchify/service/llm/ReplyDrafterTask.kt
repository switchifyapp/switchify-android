package com.enaboapps.switchify.service.llm

/**
 * The Reply Drafter [AiTask]: the prompt that asks an on-device model for
 * conversation replies, and the parser that extracts them from the output.
 */
object ReplyDrafterTask : AiTask<List<String>> {
    private const val MAX_SUGGESTIONS = 5

    private const val REPLIES_OPEN = "<replies>"
    private const val REPLIES_CLOSE = "</replies>"

    override val prompt: String = """
        You are helping someone reply in a conversation. The image is a screenshot of
        that conversation. Think through these steps, then write only the reply options.

        1. Find the other person's most recent message — this is what the user is
           replying to, and it matters most. Read the earlier messages only as
           background for the topic, tone, and relationship.
        2. The messages on the right (often a different colour) are the user's own; the
           messages on the left are from the other person. You are drafting what the
           user sends next.
        3. Write 3 to 5 replies that each directly respond to that most recent
           message — in the user's own voice, in the same language as the conversation,
           and ready to send as-is.
        4. Make the replies genuinely different from each other in stance — for example
           one that accepts or agrees, one that is neutral or asks a question, and one
           that disagrees.
        5. Always include at least one reply that lets the user say no, disagree, set a
           boundary, or not commit yet — worded to fit this conversation.
        6. Match the conversation's tone and seriousness. If it is tense, sad, awkward,
           or formal, keep the replies measured and appropriate — do not make them
           cheerful or upbeat.
        7. Notice the conversation's warmth and texting style — kisses (x, xx), emoji,
           pet names, casual abbreviations. If the other person uses them, the replies
           may use them too, including a matching sign-off, so a reply does not come
           across as cold. Keep replies plain when the conversation is plain or formal.
        8. Keep most replies short. Include a longer reply only when a brief message
           would not be enough.

        Format the answer as exactly this, and nothing else:
        $REPLIES_OPEN
        first reply
        second reply
        third reply
        $REPLIES_CLOSE
        Put each reply on its own line — a single line even for a longer reply.
        Write nothing outside the tags: no numbering, labels, preamble, or other
        text.
    """.trimIndent()

    private val listMarker = Regex("^(\\d+[.)]|[-*•])\\s*")

    /**
     * Extract the reply lines from the model output. Small models leak preamble
     * and trailing commentary despite the prompt, so only the text between the
     * reply tags is kept; everything outside is discarded. Falls back to the
     * whole output when the tags are absent, so the screen is never left empty.
     */
    override fun parse(raw: String): List<String> {
        val region = raw
            .substringAfterLast(REPLIES_OPEN, raw)
            .substringBefore(REPLIES_CLOSE)
        return region.lines()
            .map { line ->
                line.trim()
                    .replace(listMarker, "")
                    .trim()
                    .removeSurrounding("\"")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }
}
