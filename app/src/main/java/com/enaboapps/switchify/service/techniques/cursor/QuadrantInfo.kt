package com.enaboapps.switchify.service.techniques.cursor

/**
 * This data class represents the quadrant information
 * @param quadrantIndex The index of the quadrant
 * @param start The start index of the quadrant
 * @param end The end index of the quadrant
 */
data class QuadrantInfo(
    val quadrantIndex: Int,
    val start: Int,
    val end: Int,
)