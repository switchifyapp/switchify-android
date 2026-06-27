package com.enaboapps.switchify.service.scanning.tree

data class CollectionRowHint(
    val rowIndex: Int,
    val rowSpan: Int,
    val columnIndex: Int?,
    val columnSpan: Int?
)

interface CollectionRowHintProvider {
    fun getCollectionRowHint(): CollectionRowHint?
}
