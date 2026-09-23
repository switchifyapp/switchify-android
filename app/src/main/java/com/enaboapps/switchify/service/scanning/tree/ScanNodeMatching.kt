package com.enaboapps.switchify.service.scanning.tree

import com.enaboapps.switchify.service.scanning.ScanNodeInterface
import com.enaboapps.switchify.service.techniques.nodes.Node
import java.util.IdentityHashMap

internal data class ScanNodeIdentity(
    val source: String,
    val uniqueId: String?,
    val resourceId: String?,
    val className: String?,
    val path: List<Int>
)

internal interface ScanNodeIdentityProvider {
    val scanIdentity: ScanNodeIdentity?
}

internal fun matchScanNodes(
    previous: List<ScanNodeInterface>,
    next: List<ScanNodeInterface>
): Map<ScanNodeInterface, ScanNodeInterface> {
    val matches = IdentityHashMap<ScanNodeInterface, ScanNodeInterface>()
    val used = java.util.Collections.newSetFromMap(IdentityHashMap<ScanNodeInterface, Boolean>())
    val nextReferences = java.util.Collections.newSetFromMap(IdentityHashMap<ScanNodeInterface, Boolean>())
    nextReferences.addAll(next)
    previous.filter { it in nextReferences }.forEach { matches[it] = it; used.add(it) }
    val identities = IdentityHashMap<ScanNodeInterface, ScanNodeIdentity?>()
    (previous + next).forEach { identities[it] = (it as? Node)?.scanIdentity ?: (it as? ScanNodeIdentityProvider)?.scanIdentity }
    val keys: List<(ScanNodeIdentity) -> Any?> = listOf(
        { id -> id.uniqueId?.let { listOf(id.source, it) } },
        { id -> id.resourceId?.let { listOf(id.source, it, id.className) } },
        { id -> listOf(id.source, id.path, id.className) }
    )
    for ((tier, key) in keys.withIndex()) {
        val oldGroups = previous.filter { it !in matches }.mapNotNull { node ->
            identities[node]?.let(key)?.let { it to node }
        }.groupBy({ it.first }, { it.second })
        val newGroups = next.filter { it !in used }.mapNotNull { node ->
            identities[node]?.let(key)?.let { it to node }
        }.groupBy({ it.first }, { it.second })
        for ((identity, oldNodes) in oldGroups) {
            val newNodes = newGroups[identity] ?: continue
            if (oldNodes.size != 1 || newNodes.size != 1) continue
            val old = oldNodes.single()
            val fresh = newNodes.single()
            val a = identities[old]!!
            val b = identities[fresh]!!
            if (tier > 0 && a.uniqueId != b.uniqueId && (a.uniqueId != null || b.uniqueId != null)) continue
            if (tier > 1 && a.resourceId != b.resourceId) continue
            matches[old] = fresh
            used.add(fresh)
        }
    }
    return matches
}
