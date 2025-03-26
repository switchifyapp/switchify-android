package com.enaboapps.switchify.service.techniques.cursor.blocks

data class CursorBlock(
    val position: Int,
    val row: Int,
    val column: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}
