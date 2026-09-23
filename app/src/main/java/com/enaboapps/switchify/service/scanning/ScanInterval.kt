package com.enaboapps.switchify.service.scanning

data class ScanInterval(val startedAtMillis: Long, val durationMillis: Long) {
    fun remainingFraction(nowMillis: Long): Float = if (durationMillis <= 0L) 0f else
        (1f - (nowMillis - startedAtMillis).coerceAtLeast(0L).toFloat() / durationMillis)
            .coerceIn(0f, 1f)
}

data class ScanIntervalEvent(val owner: String, val generation: Long, val interval: ScanInterval?)

internal class ScanIntervalStore {
    private data class Entry(val event: ScanIntervalEvent, val sequence: Long)
    private val entries = linkedMapOf<String, Entry>()

    fun record(event: ScanIntervalEvent, sequence: Long) {
        val previous = entries[event.owner]
        if (previous != null && (previous.event.generation > event.generation ||
                previous.sequence > sequence)) return
        entries.remove(event.owner)
        entries[event.owner] = Entry(event, sequence)
        if (entries.size > 8) entries.remove(entries.keys.first())
    }

    fun intervalFor(owner: String?, afterSequence: Long): ScanInterval? =
        entries[owner]?.takeIf { it.sequence > afterSequence }?.event?.interval

    fun clear() = entries.clear()
}
