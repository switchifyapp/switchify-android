package com.enaboapps.switchify.service.techniques.pointscan.blocks

/**
 * Single source of truth for where point-scan blocks sit on screen. The block
 * manager builds its scan nodes from [blocks] and the grid view draws its
 * lines from [lineOffsets], so the highlight and the drawn grid always agree.
 *
 * Blocks use integer division, so a screen that does not divide evenly leaves
 * a few unused pixels on the right and bottom edges rather than a wider last
 * block. The drawn grid covers exactly the block area, not the whole screen.
 */
internal object PointScanGridGeometry {
    fun blockWidth(screenWidth: Int, gridSize: Int): Int = screenWidth / gridSize

    fun blockHeight(screenHeight: Int, gridSize: Int): Int = screenHeight / gridSize

    fun blocks(screenWidth: Int, screenHeight: Int, gridSize: Int): List<PointScanBlock> {
        val blockWidth = blockWidth(screenWidth, gridSize)
        val blockHeight = blockHeight(screenHeight, gridSize)
        return List(gridSize * gridSize) { index ->
            val row = index / gridSize
            val column = index % gridSize
            val left = column * blockWidth
            val top = row * blockHeight
            PointScanBlock(index, row, column, left, top, left + blockWidth, top + blockHeight)
        }
    }

    /** Positions of the interior grid lines along one axis, excluding the outer edges. */
    fun lineOffsets(extent: Int, gridSize: Int): List<Int> {
        val step = extent / gridSize
        return (1 until gridSize).map { it * step }
    }
}
