package com.enaboapps.switchify.service.techniques.nodes

internal class NodeTraversalLimitException : RuntimeException()

internal class NodeProcessingBudget(
    private val maxChildFetches: Int = 1000,
    private val maxDepth: Int = 64,
    private val timeoutNanos: Long = 1_000_000_000L,
    private val clock: () -> Long = System::nanoTime,
    private val checkCancellation: () -> Unit = {}
) {
    private val started = clock()
    private var childFetches = 0
    private var remainingText = 65_536

    fun check() {
        checkCancellation()
        if (clock() - started >= timeoutNanos) throw NodeTraversalLimitException()
    }

    fun beforeChild(depth: Int) {
        check()
        if (depth > maxDepth || childFetches >= maxChildFetches) {
            throw NodeTraversalLimitException()
        }
        childFetches++
    }

    fun text(value: CharSequence?, limit: Int = 1024): String {
        check()
        if (value == null || remainingText == 0) return ""
        val count = minOf(value.length, limit, remainingText)
        remainingText -= count
        return value.subSequence(0, count).toString()
    }

    fun identityText(value: CharSequence?): String? {
        check()
        if (value == null || value.length > 4096 || value.length > remainingText) return null
        remainingText -= value.length
        return value.toString()
    }
}

internal class NodeChildPath(
    private val parent: List<Int>,
    private val index: Int
) : AbstractList<Int>() {
    override val size: Int = parent.size + 1

    override fun get(index: Int): Int {
        if (index !in indices) throw IndexOutOfBoundsException()
        return if (index == size - 1) this.index else parent[index]
    }
}

internal data class TraversedNode<T>(
    val node: T,
    val path: List<Int>,
    val children: MutableList<Int> = ArrayList()
)

internal fun <T> traverseNodes(
    root: T,
    budget: NodeProcessingBudget,
    childCount: (T) -> Int,
    childAt: (T, Int) -> T?,
    maxNodes: Int = 1000
): List<TraversedNode<T>> {
    val nodes = ArrayList<TraversedNode<T>>(minOf(64, maxNodes))
    nodes.add(TraversedNode(root, emptyList()))
    var cursor = 0
    while (cursor < nodes.size) {
        budget.check()
        val current = nodes[cursor++]
        val count = childCount(current.node)
        for (index in 0 until count) {
            budget.beforeChild(current.path.size + 1)
            if (nodes.size >= maxNodes) throw NodeTraversalLimitException()
            val child = childAt(current.node, index) ?: continue
            current.children.add(nodes.size)
            nodes.add(TraversedNode(child, NodeChildPath(current.path, index)))
        }
    }
    budget.check()
    return nodes
}
