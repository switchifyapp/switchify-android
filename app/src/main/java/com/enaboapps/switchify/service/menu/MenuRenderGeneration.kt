package com.enaboapps.switchify.service.menu

internal class MenuRenderGeneration {
    var current = 0L
        private set
    var isOpen = false
        private set

    fun open() {
        isOpen = true
        next()
    }

    fun next() { current++ }

    fun close() {
        isOpen = false
        next()
    }

    fun isCurrent(generation: Long): Boolean = isOpen && generation == current
}
