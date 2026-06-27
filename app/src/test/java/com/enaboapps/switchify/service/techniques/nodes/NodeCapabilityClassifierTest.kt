package com.enaboapps.switchify.service.techniques.nodes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeCapabilityClassifierTest {
    @Test
    fun clickableNodeIsCurrentlyScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(isClickable = true)
        )

        assertEquals(NodeRole.Clickable, capabilities.role)
        assertTrue(capabilities.isCurrentlyScannable)
    }

    @Test
    fun clickActionNodeIsCurrentlyScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(actionIds = setOf(NodeCapabilityClassifier.ACTION_ID_CLICK))
        )

        assertEquals(NodeRole.Clickable, capabilities.role)
        assertTrue(capabilities.isCurrentlyScannable)
        assertTrue(capabilities.prefersAccessibilityClickForSelection)
    }

    @Test
    fun keyboardClickNodeDoesNotPreferAccessibilityClick() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(
                isKeyboardNode = true,
                actionIds = setOf(NodeCapabilityClassifier.ACTION_ID_CLICK)
            )
        )

        assertEquals(NodeRole.KeyboardKey, capabilities.role)
        assertTrue(capabilities.isCurrentlyScannable)
        assertFalse(capabilities.prefersAccessibilityClickForSelection)
    }

    @Test
    fun editableNodeIsClassifiedButNotNewlyScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(isEditable = true)
        )

        assertEquals(NodeRole.Editable, capabilities.role)
        assertFalse(capabilities.isCurrentlyScannable)
    }

    @Test
    fun scrollableNodeIsClassifiedButNotNewlyScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(isScrollable = true)
        )

        assertEquals(NodeRole.Scrollable, capabilities.role)
        assertFalse(capabilities.isCurrentlyScannable)
    }

    @Test
    fun checkableNodeIsClassifiedButNotNewlyScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(isCheckable = true)
        )

        assertEquals(NodeRole.Checkable, capabilities.role)
        assertFalse(capabilities.isCurrentlyScannable)
    }

    @Test
    fun collectionNodeIsClassifiedButNotNewlyScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(hasCollectionInfo = true)
        )

        assertEquals(NodeRole.Collection, capabilities.role)
        assertFalse(capabilities.isCurrentlyScannable)
    }

    @Test
    fun collectionMetadataIsRecorded() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(
                hasCollectionInfo = true,
                collectionMetadata = CollectionMetadata(
                    rowCount = 12,
                    columnCount = 2,
                    isHierarchical = true,
                    selectionMode = 1
                )
            )
        )

        val collection = capabilities.collectionMetadata?.collection
        assertEquals(12, collection?.rowCount)
        assertEquals(2, collection?.columnCount)
        assertEquals(true, collection?.isHierarchical)
        assertEquals(1, collection?.selectionMode)
    }

    @Test
    fun collectionItemMetadataIsRecorded() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(
                hasCollectionItemInfo = true,
                collectionItemMetadata = CollectionItemMetadata(
                    rowIndex = 4,
                    rowSpan = 1,
                    columnIndex = 2,
                    columnSpan = 3,
                    isHeading = true,
                    isSelected = true
                )
            )
        )

        val collectionItem = capabilities.collectionMetadata?.collectionItem
        assertEquals(4, collectionItem?.rowIndex)
        assertEquals(1, collectionItem?.rowSpan)
        assertEquals(2, collectionItem?.columnIndex)
        assertEquals(3, collectionItem?.columnSpan)
        assertEquals(true, collectionItem?.isHeading)
        assertEquals(true, collectionItem?.isSelected)
    }

    @Test
    fun collectionRoleStillUsesExistingRules() {
        val collectionCapabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(
                hasCollectionInfo = true,
                collectionMetadata = CollectionMetadata(
                    rowCount = 3,
                    columnCount = 1,
                    isHierarchical = false,
                    selectionMode = 0
                )
            )
        )
        val collectionItemCapabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(
                hasCollectionItemInfo = true,
                collectionItemMetadata = CollectionItemMetadata(
                    rowIndex = 2,
                    rowSpan = 1,
                    columnIndex = 0,
                    columnSpan = 1,
                    isHeading = false,
                    isSelected = false
                )
            )
        )

        assertEquals(NodeRole.Collection, collectionCapabilities.role)
        assertEquals(NodeRole.CollectionItem, collectionItemCapabilities.role)
    }

    @Test
    fun collectionMetadataDoesNotMakeNodeScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(
                hasCollectionInfo = true,
                collectionMetadata = CollectionMetadata(
                    rowCount = 6,
                    columnCount = 2,
                    isHierarchical = false,
                    selectionMode = 0
                )
            )
        )

        assertFalse(capabilities.isCurrentlyScannable)
    }

    @Test
    fun collectionItemMetadataDoesNotMakeNodeScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(
                hasCollectionItemInfo = true,
                collectionItemMetadata = CollectionItemMetadata(
                    rowIndex = 1,
                    rowSpan = 1,
                    columnIndex = 0,
                    columnSpan = 1,
                    isHeading = false,
                    isSelected = false
                )
            )
        )

        assertFalse(capabilities.isCurrentlyScannable)
    }

    @Test
    fun customActionIsRecordedButNotNewlyScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(actionIds = setOf(123456))
        )

        assertTrue(capabilities.hasCustomActions)
        assertFalse(capabilities.isCurrentlyScannable)
    }

    @Test
    fun focusableNodePreservesCurrentScannability() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(isFocusable = true)
        )

        assertEquals(NodeRole.Focusable, capabilities.role)
        assertTrue(capabilities.isCurrentlyScannable)
    }

    @Test
    fun textOnlyNodeIsClassifiedButNotScannable() {
        val capabilities = NodeCapabilityClassifier.classifyFacts(
            RawNodeCapabilityFacts(hasText = true)
        )

        assertEquals(NodeRole.TextOnly, capabilities.role)
        assertFalse(capabilities.isCurrentlyScannable)
    }
}
