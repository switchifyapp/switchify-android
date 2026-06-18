package com.enaboapps.switchify.service.core

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AccessibilityEventPipeline(
    private val scope: CoroutineScope,
    private val settledRefreshDelayMs: Long = SETTLED_REFRESH_DELAY_MS,
    private val eventDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onProcess: suspend () -> Unit
) {
    private val channel = Channel<Unit>(capacity = Channel.CONFLATED)
    private val pendingSettledRefresh = AtomicBoolean(false)
    private var job: Job? = null
    private var settledRefreshJob: Job? = null

    private companion object {
        private const val SETTLED_REFRESH_DELAY_MS = 250L
    }

    fun start() {
        job?.cancel()
        job = scope.launch {
            channel
                .consumeAsFlow()
                .flowOn(eventDispatcher)
                .collect {
                    if (scope.isActive) {
                        onProcess()
                        if (pendingSettledRefresh.getAndSet(false)) {
                            scheduleSettledRefresh()
                        }
                    }
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        settledRefreshJob?.cancel()
        settledRefreshJob = null
        pendingSettledRefresh.set(false)
    }

    fun trySend(event: AccessibilityEvent) {
        trySendEventType(event.eventType)
    }

    internal fun trySendEventType(eventType: Int) {
        if (shouldScheduleSettledRefresh(eventType)) {
            pendingSettledRefresh.set(true)
        }
        channel.trySend(Unit)
    }

    private fun scheduleSettledRefresh() {
        settledRefreshJob?.cancel()
        settledRefreshJob = scope.launch {
            delay(settledRefreshDelayMs)
            if (scope.isActive) {
                onProcess()
            }
        }
    }

    internal fun shouldScheduleSettledRefresh(eventType: Int): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SELECTED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
    }
}
