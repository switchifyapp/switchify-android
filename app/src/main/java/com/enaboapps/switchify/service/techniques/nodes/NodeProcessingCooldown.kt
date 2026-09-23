package com.enaboapps.switchify.service.techniques.nodes

internal class NodeProcessingCooldown(private val clock: () -> Long = System::nanoTime) {
    private var source: String? = null
    private var failures = 0
    private var until: Long? = null

    @Synchronized
    fun canProcess(source: String): Boolean {
        if (this.source != source) {
            reset()
            this.source = source
        }
        return until?.let { clock() - it >= 0 } ?: true
    }

    @Synchronized
    fun failure(source: String) {
        if (this.source != source) return
        failures++
        if (failures >= 5) {
            until = clock() + 10_000_000_000L
            failures = 0
        }
    }

    @Synchronized
    fun success() {
        failures = 0
        until = null
    }

    @Synchronized
    fun reset() {
        source = null
        success()
    }
}
