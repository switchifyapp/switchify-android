package com.enaboapps.switchify.pc

internal class PcResolveSequencer<T> {
    private val queue = ArrayDeque<T>()
    private var active = false

    fun enqueue(item: T): T? = synchronized(this) {
        queue.addLast(item)
        takeNextLocked()
    }

    fun finish(): T? = synchronized(this) {
        active = false
        takeNextLocked()
    }

    fun clear(): Unit = synchronized(this) {
        queue.clear()
        active = false
    }

    private fun takeNextLocked(): T? {
        if (active) return null
        val next = queue.removeFirstOrNull() ?: return null
        active = true
        return next
    }
}
