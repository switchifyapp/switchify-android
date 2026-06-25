package com.enaboapps.switchify.service.techniques.nodes

internal data class NodeCollectionMetadata(
    val collection: CollectionMetadata? = null,
    val collectionItem: CollectionItemMetadata? = null
)

internal data class CollectionMetadata(
    val rowCount: Int,
    val columnCount: Int,
    val isHierarchical: Boolean,
    val selectionMode: Int
)

internal data class CollectionItemMetadata(
    val rowIndex: Int,
    val rowSpan: Int,
    val columnIndex: Int,
    val columnSpan: Int,
    val isHeading: Boolean,
    val isSelected: Boolean
)
