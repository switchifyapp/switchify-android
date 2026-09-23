package com.enaboapps.switchify.service.core

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AccessibilityEventPipeline(
    private val scope: CoroutineScope,
    private val settledRefreshDelayMs: Long = 250L,
    private val eventDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val minimumRefreshIntervalMs: Long = 0L,
    private val onError: (Exception) -> Unit = {},
    private val onProcess: suspend (() -> Boolean) -> Unit
) {
    private val channel = Channel<Unit>(Channel.CONFLATED)
    private val pendingSettledRefresh = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val suspended = AtomicBoolean(false)
    private val generation = AtomicLong()
    private val processing = Mutex()
    private var job: Job? = null
    private var settledRefreshJob: Job? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        job = scope.launch(eventDispatcher) {
            for (signal in channel) {
                if (!running.get()) break
                if (suspended.get()) continue
                processing.withLock {
                    val current = generation.get()
                    try {
                        onProcess { running.get() && !suspended.get() && generation.get() == current && isActive }
                    } catch (error: CancellationException) {
                        if (!isActive) throw error
                    } catch (error: Exception) {
                        onError(error)
                    }
                }
                if (pendingSettledRefresh.getAndSet(false)) scheduleSettledRefresh()
                if (minimumRefreshIntervalMs > 0) delay(minimumRefreshIntervalMs)
            }
        }
    }

    fun stop() {
        running.set(false)
        generation.incrementAndGet()
        job?.cancel()
        job = null
        settledRefreshJob?.cancel()
        settledRefreshJob = null
        pendingSettledRefresh.set(false)
        while (channel.tryReceive().isSuccess) Unit
    }

    fun requestRefresh() {
        if (running.get() && !suspended.get()) channel.trySend(Unit)
    }

    fun setSuspended(value: Boolean) {
        suspended.set(value)
        generation.incrementAndGet()
        if (!value) requestRefresh()
    }

    fun trySend(event: AccessibilityEvent) = trySendEventType(event.eventType)

    internal fun trySendEventType(eventType: Int) {
        if (!running.get() || !shouldProcess(eventType)) return
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) generation.incrementAndGet()
        if (shouldScheduleSettledRefresh(eventType)) pendingSettledRefresh.set(true)
        requestRefresh()
    }

    private fun scheduleSettledRefresh() {
        settledRefreshJob?.cancel()
        settledRefreshJob = scope.launch(eventDispatcher) {
            delay(settledRefreshDelayMs)
            requestRefresh()
        }
    }

    @Suppress("DEPRECATION")
    internal fun shouldProcess(eventType: Int): Boolean = when (eventType) {
        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
        AccessibilityEvent.TYPE_ANNOUNCEMENT,
        AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START,
        AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END,
        AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
        AccessibilityEvent.TYPE_GESTURE_DETECTION_END,
        AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
        AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> false
        else -> true
    }

    internal fun shouldScheduleSettledRefresh(eventType: Int): Boolean =
        eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SELECTED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
}
