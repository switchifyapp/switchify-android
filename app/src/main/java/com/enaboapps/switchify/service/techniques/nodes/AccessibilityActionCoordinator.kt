package com.enaboapps.switchify.service.techniques.nodes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal interface AccessibilityActionMenuActions {
    suspend fun open(target: NodeActionTarget, isCurrent: () -> Boolean)
    suspend fun replace(target: NodeActionTarget, isCurrent: () -> Boolean)
    suspend fun showMainWithoutActions(isCurrent: () -> Boolean)
    suspend fun closeAll(isCurrent: () -> Boolean)
}

internal class AccessibilityActionCoordinator(
    private val scope: CoroutineScope,
    private val resolver: NodeActionResolver,
    private val menuActions: AccessibilityActionMenuActions
) {
    private val stateLock = Any()
    private var generation = 0L
    private var resolving = false
    private var resolutionJob: Job? = null

    fun open(locator: NodeActionLocator) {
        val operationGeneration = beginOperation() ?: return
        launchOperation(operationGeneration) {
            try {
                val resolution = resolveSafely(locator, emptySet())
                if (!isCurrent(operationGeneration)) return@launchOperation
                when (resolution) {
                    NodeActionResolution.SourceMissing -> close(operationGeneration)
                    NodeActionResolution.TargetMissing -> showMain(operationGeneration)
                    is NodeActionResolution.Resolved -> {
                        val target = NodeActionTarget(locator, resolution.target.actions)
                        if (target.actions.isEmpty()) {
                            showMain(operationGeneration)
                        } else {
                            menuActions.open(target) { isCurrent(operationGeneration) }
                        }
                    }
                }
            } finally {
                finishOperation(operationGeneration)
            }
        }
    }

    fun select(target: NodeActionTarget, actionId: Int) {
        val operationGeneration = beginOperation() ?: return
        launchOperation(operationGeneration) {
            try {
                val resolution = resolveSafely(target.locator, target.excludedActionIds)
                if (!isCurrent(operationGeneration)) return@launchOperation
                when (resolution) {
                    NodeActionResolution.SourceMissing -> close(operationGeneration)
                    NodeActionResolution.TargetMissing -> showMain(operationGeneration)
                    is NodeActionResolution.Resolved -> handleResolvedSelection(
                        target,
                        actionId,
                        resolution.target,
                        operationGeneration
                    )
                }
            } finally {
                finishOperation(operationGeneration)
            }
        }
    }

    fun cancel() {
        synchronized(stateLock) {
            generation += 1
            resolving = false
            resolutionJob?.cancel()
            resolutionJob = null
        }
    }

    internal fun isResolving(): Boolean = synchronized(stateLock) { resolving }

    private fun launchOperation(operationGeneration: Long, block: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        synchronized(stateLock) {
            if (generation == operationGeneration && resolving) {
                resolutionJob = job
            } else {
                job.cancel()
            }
        }
        job.start()
    }

    private suspend fun handleResolvedSelection(
        target: NodeActionTarget,
        actionId: Int,
        resolvedTarget: ResolvedNodeActionTarget,
        operationGeneration: Long
    ) {
        if (resolvedTarget.actions.none { it.id == actionId }) {
            refreshOrShowMain(
                target.copy(actions = resolvedTarget.actions),
                operationGeneration
            )
            return
        }
        val performed = try {
            resolvedTarget.perform(actionId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (performed) {
            close(operationGeneration)
            return
        }
        val excludedActionIds = target.excludedActionIds + actionId
        refreshOrShowMain(
            NodeActionTarget(
                locator = target.locator,
                actions = resolvedTarget.actions.filterNot { it.id == actionId },
                excludedActionIds = excludedActionIds
            ),
            operationGeneration
        )
    }

    private suspend fun refreshOrShowMain(
        target: NodeActionTarget,
        operationGeneration: Long
    ) {
        if (target.actions.isEmpty()) {
            showMain(operationGeneration)
        } else {
            menuActions.replace(target) { isCurrent(operationGeneration) }
        }
    }

    private suspend fun showMain(operationGeneration: Long) {
        menuActions.showMainWithoutActions { isCurrent(operationGeneration) }
    }

    private suspend fun close(operationGeneration: Long) {
        menuActions.closeAll { isCurrent(operationGeneration) }
    }

    private fun beginOperation(): Long? = synchronized(stateLock) {
        if (resolving) return null
        generation += 1
        resolving = true
        generation
    }

    private fun finishOperation(operationGeneration: Long) {
        synchronized(stateLock) {
            if (generation == operationGeneration) {
                resolving = false
                resolutionJob = null
            }
        }
    }

    private fun isCurrent(operationGeneration: Long): Boolean = synchronized(stateLock) {
        generation == operationGeneration && resolving
    }

    private suspend fun resolveSafely(
        locator: NodeActionLocator,
        excludedActionIds: Set<Int>
    ): NodeActionResolution = runCatching {
        resolver.resolve(locator, excludedActionIds)
    }.getOrElse {
        if (it is CancellationException) throw it
        if (it is NodeTraversalLimitException) return@getOrElse NodeActionResolution.TargetMissing
        NodeActionResolution.SourceMissing
    }
}
