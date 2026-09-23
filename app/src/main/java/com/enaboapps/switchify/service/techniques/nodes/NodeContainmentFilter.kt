package com.enaboapps.switchify.service.techniques.nodes

internal data class NodeRectangle(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Long get() = left.toLong() + width
    val bottom: Long get() = top.toLong() + height

    fun contains(other: NodeRectangle): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

private class NodeRectangleIndex(rectangles: List<NodeRectangle>, depth: Int = 0) {
    private val left = rectangles.minOf { it.left }
    private val top = rectangles.minOf { it.top }
    private val right = rectangles.maxOf { it.right }
    private val bottom = rectangles.maxOf { it.bottom }
    private val uniform = rectangles.distinct().size == 1
    private val leaf = if (rectangles.size <= 8 || uniform) rectangles else emptyList()
    private val children = if (leaf.isEmpty()) {
        val sorted = if (depth % 2 == 0) rectangles.sortedBy { it.left } else rectangles.sortedBy { it.top }
        val middle = sorted.size / 2
        listOf(NodeRectangleIndex(sorted.subList(0, middle), depth + 1),
            NodeRectangleIndex(sorted.subList(middle, sorted.size), depth + 1))
    } else emptyList()

    fun containsSmaller(outer: NodeRectangle, check: () -> Unit): Boolean {
        check()
        if (left > outer.right || top > outer.bottom || right < outer.left || bottom < outer.top) return false
        if (!uniform && outer.left <= left && outer.top <= top && outer.right >= right && outer.bottom >= bottom) return true
        return if (leaf.isNotEmpty()) {
            leaf.any { it != outer && outer.contains(it) }
        } else {
            children.any { it.containsSmaller(outer, check) }
        }
    }
}

internal fun <T> filterContainedNodes(
    nodes: List<T>,
    check: () -> Unit = {},
    bounds: (T) -> NodeRectangle
): List<T> {
    if (nodes.size < 2) return nodes
    val rectangles = nodes.map(bounds)
    val valid = rectangles.filter { it.width > 0 && it.height > 0 }
    if (valid.isEmpty()) return nodes
    val index = NodeRectangleIndex(valid)
    return nodes.filterIndexed { position, _ ->
        val rectangle = rectangles[position]
        rectangle.width <= 0 || rectangle.height <= 0 || !index.containsSmaller(rectangle, check)
    }
}
