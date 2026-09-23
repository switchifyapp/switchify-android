package com.enaboapps.switchify.service.scanning.preferences

import android.content.SharedPreferences
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ScanPreferenceChangeCoordinator(
    private val source: ScanPreferenceChangeSource,
    private val scope: CoroutineScope,
    private val uiDispatcher: CoroutineDispatcher,
    private val applyPlan: (ScanPreferenceUpdatePlan) -> Unit,
    private val onApplied: () -> Unit,
    private val onApplyFailed: (Exception) -> Unit,
    private val batchDelayMillis: Long = 16L
) {
    private val pendingKeys = mutableSetOf<String>()
    private val lock = Any()
    private var batchJob: Job? = null
    private var started = false
    private var generation = 0L

    fun start() {
        synchronized(lock) {
            if (started) return
            batchJob?.cancel()
            batchJob = null
            pendingKeys.clear()
            started = true
            generation++
            val sourceGeneration = generation
            source.start { key -> enqueue(key, sourceGeneration) }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!started) return
            started = false
            generation++
            source.stop()
            batchJob?.cancel()
            batchJob = null
            pendingKeys.clear()
        }
    }

    fun enqueue(key: String) {
        synchronized(lock) {
            enqueueLocked(key, generation)
        }
    }

    private fun enqueue(key: String, sourceGeneration: Long) {
        synchronized(lock) {
            enqueueLocked(key, sourceGeneration)
        }
    }

    private fun enqueueLocked(key: String, sourceGeneration: Long) {
        val effect = ScanPreferencePolicy.effectFor(key)
        if (!started || generation != sourceGeneration || effect == null) {
            return
        }
        pendingKeys.add(key)
        if (batchJob?.isActive == true) return
        batchJob = scope.launch {
            while (true) {
                delay(batchDelayMillis)
                val keys = synchronized(lock) {
                    if (!isCurrentGenerationLocked(sourceGeneration)) null else {
                        pendingKeys.toSet().also { pendingKeys.clear() }
                    }
                } ?: return@launch
                val plan = ScanPreferencePolicy.reduce(keys)
                withContext(uiDispatcher) {
                    if (!isCurrentGeneration(sourceGeneration) || plan.isEmpty) return@withContext
                    applySafely(plan, sourceGeneration)
                }
                val drained = synchronized(lock) {
                    if (!isCurrentGenerationLocked(sourceGeneration)) return@launch
                    pendingKeys.isEmpty().also {
                        if (it) batchJob = null
                    }
                }
                if (drained) return@launch
            }
        }
    }

    private fun applySafely(plan: ScanPreferenceUpdatePlan, sourceGeneration: Long) {
        try {
            applyPlan(plan)
            if (isCurrentGeneration(sourceGeneration)) onApplied()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isCurrentGeneration(sourceGeneration)) {
                runCatching { onApplyFailed(error) }
            }
        }
    }

    private fun isCurrentGeneration(sourceGeneration: Long): Boolean = synchronized(lock) {
        isCurrentGenerationLocked(sourceGeneration)
    }

    private fun isCurrentGenerationLocked(sourceGeneration: Long): Boolean =
        started && generation == sourceGeneration
}

internal interface ScanPreferenceChangeSource {
    fun start(onChanged: (String) -> Unit)
    fun stop()
}

internal class PreferenceManagerScanPreferenceChangeSource(
    private val preferenceManager: PreferenceManager
) : ScanPreferenceChangeSource {
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun start(onChanged: (String) -> Unit) {
        if (listener != null) return
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null) onChanged(key)
        }.also(preferenceManager::registerChangeListener)
    }

    override fun stop() {
        listener?.let(preferenceManager::unregisterChangeListener)
        listener = null
    }
}
