package com.enaboapps.switchify.service.core

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AccessibilityEventPipeline(
    private val scope: CoroutineScope,
    private val onProcess: suspend () -> Unit
) {
    private val channel = Channel<AccessibilityEvent>(capacity = Channel.CONFLATED)
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            channel
                .consumeAsFlow()
                .flowOn(Dispatchers.Default)
                .collect { if (scope.isActive) onProcess() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun trySend(event: AccessibilityEvent) {
        channel.trySend(event)
    }
}

