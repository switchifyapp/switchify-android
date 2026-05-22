package com.enaboapps.switchify.service.llm

import android.graphics.Bitmap

object ReplyDrafterPrompt {
    private const val MAX_SUGGESTIONS = 5
    private const val MAX_IMAGE_DIMENSION = 1024

    val PROMPT = """
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

        Output only the replies, one per line. Keep each reply on a single line, even a
        longer one. Do not add numbering, labels, preamble, or any other text.
    """.trimIndent()

    private val listMarker = Regex("^(\\d+[.)]|[-*•])\\s*")

    fun parseSuggestions(text: String): List<String> {
        return text.lines()
            .map { it.trim().replace(listMarker, "").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }

    fun downscale(bitmap: Bitmap): Bitmap {
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        if (longestEdge <= MAX_IMAGE_DIMENSION) return bitmap
        val scale = MAX_IMAGE_DIMENSION.toFloat() / longestEdge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
