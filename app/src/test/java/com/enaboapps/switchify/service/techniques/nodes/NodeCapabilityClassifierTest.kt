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
