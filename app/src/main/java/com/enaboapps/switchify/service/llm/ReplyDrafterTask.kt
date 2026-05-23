package com.enaboapps.switchify.service.llm

/**
 * The Reply Drafter [AiTask]: the prompt that asks an on-device model for
 * conversation replies, and the parser that extracts them from the output.
 *
 * The first draft is one-shot: an empty [previousReplies] uses the rules
 * block + format block verbatim — byte-for-byte identical to the original
 * un-guided prompt.
 *
 * Refinement is iterative: pass the previous draft's replies in
 * [previousReplies] plus the user's [refinement] note. The prompt then
 * shows the model what it produced before and asks for a new set that
 * applies the note. This makes the refinement note's role unambiguous —
 * the model can't mistake it for "the message to reply to" because the
 * actual conversation is in the image and the previous replies sit
 * between the rules and the refinement.
 */
data class ReplyDrafterTask(
    val previousReplies: List<String> = emptyList(),
    val refinement: String = ""
) : AiTask<List<String>> {

    override val prompt: String
        get() {
            val refinementBlock = buildRefinementBlock()
            return if (refinementBlock.isEmpty()) {
                "$RULES_BLOCK\n\n$FORMAT_BLOCK"
            } else {
                "$RULES_BLOCK\n\n$refinementBlock\n\n$FORMAT_BLOCK"
            }
        }

    private fun buildRefinementBlock(): String {
        if (previousReplies.isEmpty()) return ""
        val previousList = previousReplies
            .mapIndexed { i, r -> "${i + 1}. $r" }
            .joinToString("\n")
        val direction = if (refinement.isBlank()) {
            "The user has asked for a different set, with no specific direction — produce a meaningfully different spread."
        } else {
            "The user has asked for a different set with this direction: \"${refinement.trim()}\". " +
                "This direction is from the user to you about how the replies should change — it is " +
                "NOT a message in the conversation, and you must NOT treat it as a message to reply to."
        }
        return """
            REFINEMENT. Earlier you wrote these reply options for this same conversation:
            $previousList

            $direction

            Write a new set of 3 to 5 replies that reflects the user's direction while still
            satisfying rules 1 to 8 above and replying to the most recent message in the image.
            The new replies must be meaningfully different from the previous set, not
            rephrasings of them.
        """.trimIndent()
    }

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

    companion object {
        private const val MAX_SUGGESTIONS = 5

        private const val REPLIES_OPEN = "<replies>"
        private const val REPLIES_CLOSE = "</replies>"

        private val listMarker = Regex("^(\\d+[.)]|[-*•])\\s*")

        private val RULES_BLOCK: String = """
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
        """.trimIndent()

        private val FORMAT_BLOCK: String = """
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
    }
}
