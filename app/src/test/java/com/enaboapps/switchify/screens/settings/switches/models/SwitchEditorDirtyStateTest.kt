package com.enaboapps.switchify.screens.settings.switches.models

import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.switches.SwitchAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchEditorDirtyStateTest {
    @Test
    fun cameraEditorTracksEachEditableField() {
        val nameModel = AddEditCameraSwitchScreenModel()
        val gestureModel = AddEditCameraSwitchScreenModel()
        val actionModel = AddEditCameraSwitchScreenModel()

        assertFalse(nameModel.hasUnsavedChanges.value)
        nameModel.updateName("Smile")
        gestureModel.setGesture(CameraSwitchFacialGesture(CameraSwitchFacialGesture.BLINK))
        actionModel.setAction(SwitchAction(SwitchAction.ACTION_SELECT))

        assertTrue(nameModel.hasUnsavedChanges.value)
        assertTrue(gestureModel.hasUnsavedChanges.value)
        assertTrue(actionModel.hasUnsavedChanges.value)
    }
}
