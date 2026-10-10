package com.enaboapps.switchify.pc.remote

class PcLiveTypingController(private val stream: PcLiveTypingStream) {
    private val queue = PcSerialQueue()
    private var applied = ""
    private var desired = ""

    suspend fun update(next: String): Boolean {
        desired = next
        return queue.enqueue { reconcile() }
    }

    suspend fun submitLine(): Boolean = queue.enqueue {
        if (!reconcile()) return@enqueue false
        if (!stream.streamKey(ENTER)) return@enqueue false
        applied = ""
        desired = ""
        true
    }

    fun applied(): String = applied

    private suspend fun reconcile(): Boolean {
        while (applied != desired) {
            val current = codePoints(applied)
            val target = codePoints(desired)
            var prefix = 0
            while (prefix < current.size && prefix < target.size && current[prefix] == target[prefix]) prefix += 1
            for (count in current.size downTo prefix + 1) {
                if (!stream.streamKey(BACKSPACE)) return false
                applied = codePoints(applied).dropLast(1).joinToString("")
            }
            val inserted = target.drop(prefix).joinToString("")
            if (inserted.isNotEmpty()) {
                if (!stream.streamChunk(inserted)) return false
                applied = target.joinToString("")
            }
        }
        return true
    }

    private fun codePoints(text: String): List<String> =
        text.codePoints().toArray().map { String(Character.toChars(it)) }

    companion object {
        const val ENTER = "Enter"
        const val BACKSPACE = "Backspace"
        const val MAX_TEXT_LENGTH = 2_000
    }
}
