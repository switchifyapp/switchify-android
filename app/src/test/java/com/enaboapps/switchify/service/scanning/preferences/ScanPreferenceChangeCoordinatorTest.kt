package com.enaboapps.switchify.service.scanning.preferences

import com.enaboapps.switchify.backend.preferences.PreferenceManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class ScanPreferenceChangeCoordinatorTest {
    @Test
    fun batchesDownloadedChangesAndAppliesOneReducedPlan() = runTest {
        val source = FakeSource()
        val plans = mutableListOf<ScanPreferenceUpdatePlan>()
        var notificationCount = 0
        val coordinator = coordinator(source, plans, { notificationCount++ })
        coordinator.start()

        source.emit(PreferenceManager.PREFERENCE_KEY_GROUP_SCAN)
        source.emit(PreferenceManager.PREFERENCE_KEY_SCAN_RATE)
        source.emit(PreferenceManager.PREFERENCE_KEY_GROUP_SCAN)
        advanceTimeBy(16)
        runCurrent()

        assertEquals(1, plans.size)
        assertTrue(plans.single().contains(ScanPreferenceEffect.REFRESH_ITEM_STRUCTURE))
        assertFalse(plans.single().contains(ScanPreferenceEffect.REFRESH_ITEM_TIMING))
        assertEquals(1, notificationCount)
    }

    @Test
    fun ignoresUnknownKeys() = runTest {
        val source = FakeSource()
        val plans = mutableListOf<ScanPreferenceUpdatePlan>()
        val coordinator = coordinator(source, plans)
        coordinator.start()

        source.emit("unrelated")
        advanceTimeBy(16)
        runCurrent()

        assertTrue(plans.isEmpty())
    }

    @Test
    fun stopCancelsQueuedChangesAndUnregistersSource() = runTest {
        val source = FakeSource()
        val plans = mutableListOf<ScanPreferenceUpdatePlan>()
        val coordinator = coordinator(source, plans)
        coordinator.start()
        source.emit(PreferenceManager.PREFERENCE_KEY_CURSOR_MODE)

        coordinator.stop()
        advanceTimeBy(16)
        runCurrent()

        assertTrue(plans.isEmpty())
        assertFalse(source.started)
    }

    @Test
    fun startAndStopAreIdempotent() = runTest {
        val source = FakeSource()
        val coordinator = coordinator(source, mutableListOf())

        coordinator.start()
        coordinator.start()
        coordinator.stop()
        coordinator.stop()

        assertEquals(1, source.startCount)
        assertEquals(1, source.stopCount)
    }

    @Test
    fun appliesBatchOnlyOnUiDispatcher() = runTest {
        val source = FakeSource()
        val plans = mutableListOf<ScanPreferenceUpdatePlan>()
        val uiDispatcher = QueuedDispatcher()
        val coordinator = ScanPreferenceChangeCoordinator(
            source = source,
            scope = this,
            uiDispatcher = uiDispatcher,
            applyPlan = plans::add,
            onApplied = {},
            onApplyFailed = {}
        )
        coordinator.start()

        source.emit(PreferenceManager.PREFERENCE_KEY_GROUP_SCAN)
        advanceTimeBy(16)
        runCurrent()
        assertTrue(plans.isEmpty())

        uiDispatcher.runNext()
        assertEquals(1, plans.size)
    }

    @Test
    fun appliesChangesQueuedDuringUiHandoffInANewBatch() = runTest {
        val source = FakeSource()
        val plans = mutableListOf<ScanPreferenceUpdatePlan>()
        val uiDispatcher = QueuedDispatcher()
        val coordinator = ScanPreferenceChangeCoordinator(
            source = source,
            scope = this,
            uiDispatcher = uiDispatcher,
            applyPlan = plans::add,
            onApplied = {},
            onApplyFailed = {}
        )
        coordinator.start()

        source.emit(PreferenceManager.PREFERENCE_KEY_GROUP_SCAN)
        advanceTimeBy(16)
        runCurrent()
        source.emit(PreferenceManager.PREFERENCE_KEY_CURSOR_MODE)
        uiDispatcher.runNext()

        advanceTimeBy(16)
        runCurrent()
        uiDispatcher.runNext()

        assertEquals(2, plans.size)
        assertTrue(plans[0].contains(ScanPreferenceEffect.REFRESH_ITEM_STRUCTURE))
        assertTrue(plans[1].contains(ScanPreferenceEffect.REFRESH_POINT_STRUCTURE))
    }

    @Test
    fun containsApplyFailureAndDoesNotNotifySuccess() = runTest {
        val source = FakeSource()
        val failures = mutableListOf<Exception>()
        var notificationCount = 0
        val expectedFailure = IllegalStateException("failed")
        val coordinator = ScanPreferenceChangeCoordinator(
            source = source,
            scope = this,
            uiDispatcher = StandardTestDispatcher(testScheduler),
            applyPlan = { throw expectedFailure },
            onApplied = { notificationCount++ },
            onApplyFailed = failures::add
        )
        coordinator.start()

        source.emit(PreferenceManager.PREFERENCE_KEY_GROUP_SCAN)
        advanceTimeBy(16)
        runCurrent()

        assertEquals(listOf(expectedFailure), failures)
        assertEquals(0, notificationCount)
    }

    @Test
    fun notifiesSuccessOnlyAfterPlanApplies() = runTest {
        val source = FakeSource()
        val calls = mutableListOf<String>()
        val coordinator = ScanPreferenceChangeCoordinator(
            source = source,
            scope = this,
            uiDispatcher = StandardTestDispatcher(testScheduler),
            applyPlan = { calls.add("apply") },
            onApplied = { calls.add("notify") },
            onApplyFailed = { calls.add("failure") }
        )
        coordinator.start()

        source.emit(PreferenceManager.PREFERENCE_KEY_GROUP_SCAN)
        advanceTimeBy(16)
        runCurrent()

        assertEquals(listOf("apply", "notify"), calls)
    }

    @Test
    fun restartClearsPendingKeysAndRejectsStoppedSourceCallbacks() = runTest {
        val source = FakeSource()
        val plans = mutableListOf<ScanPreferenceUpdatePlan>()
        val coordinator = coordinator(source, plans)
        coordinator.start()
        source.emit(PreferenceManager.PREFERENCE_KEY_GROUP_SCAN)

        coordinator.stop()
        coordinator.start()
        source.emitStopped(PreferenceManager.PREFERENCE_KEY_SCAN_RATE)
        advanceTimeBy(16)
        runCurrent()

        assertTrue(plans.isEmpty())

        source.emit(PreferenceManager.PREFERENCE_KEY_CURSOR_MODE)
        advanceTimeBy(16)
        runCurrent()

        assertEquals(1, plans.size)
        assertTrue(plans.single().contains(ScanPreferenceEffect.REFRESH_POINT_STRUCTURE))
    }

    private fun TestScope.coordinator(
        source: FakeSource,
        plans: MutableList<ScanPreferenceUpdatePlan>,
        onApplied: () -> Unit = {}
    ) = ScanPreferenceChangeCoordinator(
        source = source,
        scope = this,
        uiDispatcher = StandardTestDispatcher(testScheduler),
        applyPlan = plans::add,
        onApplied = onApplied,
        onApplyFailed = {}
    )

    private class FakeSource : ScanPreferenceChangeSource {
        private var listener: ((String) -> Unit)? = null
        private var stoppedListener: ((String) -> Unit)? = null
        var started = false
        var startCount = 0
        var stopCount = 0

        override fun start(onChanged: (String) -> Unit) {
            listener = onChanged
            started = true
            startCount++
        }

        override fun stop() {
            stoppedListener = listener
            listener = null
            started = false
            stopCount++
        }

        fun emit(key: String) {
            listener?.invoke(key)
        }

        fun emitStopped(key: String) {
            stoppedListener?.invoke(key)
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runNext() {
            tasks.removeFirst().run()
        }
    }
}
