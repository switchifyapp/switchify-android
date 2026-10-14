package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.enaboapps.switchify.R
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SupportedActionsPolicy
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.switches.SwitchNameGenerator

class AddEditCameraSwitchScreenModel : ViewModel() {

    companion object {
        private const val TAG = "AddEditCameraSwitchScreenModel"
    }

    private var customName: String? = null
    val name = mutableStateOf("")
    val selectedGesture = mutableStateOf<CameraSwitchFacialGesture?>(
        CameraSwitchFacialGesture(CameraSwitchFacialGesture.SMILE)
    )
    val action = mutableStateOf(SwitchAction(SwitchAction.ACTION_SELECT))
    val isValid = mutableStateOf(false)
    val hasUnsavedChanges = mutableStateOf(false)
    val showDeleteConfirmation = mutableStateOf(false)

    private lateinit var store: SwitchEventStore
    private var code: String? = null
    private var profileId: String? = null

    fun init(code: String?, context: Context, profileId: String? = null) {
        store = SwitchEventStore.getInstance()
        this.code = code
        this.profileId = profileId

        if (code != null) {
            val event = store.find(code, profileId)
            event?.let {
                customName = it.name.takeIf { stored -> stored.isNotBlank() }
                selectedGesture.value = CameraSwitchFacialGesture(it.code)
                val allowed = SupportedActionsPolicy.supportedActionIds(context)
                action.value =
                    if (allowed.contains(it.pressAction.id)) it.pressAction else SwitchAction(
                        SwitchAction.ACTION_SELECT
                    )
            }
        } else {
            customName = null
        }
        refreshName()
        validate()
    }

    fun updateName(newName: String) {
        customName = newName.trim().takeIf { it.isNotBlank() }
        refreshName()
        hasUnsavedChanges.value = true
        validate()
    }

    private fun refreshName() {
        name.value = customName ?: SwitchNameGenerator.forGesture(selectedGesture.value)
    }

    fun setGesture(gesture: CameraSwitchFacialGesture) {
        selectedGesture.value = gesture
        refreshName()
        hasUnsavedChanges.value = true
        validate()
    }

    fun setAction(newAction: SwitchAction) {
        action.value = newAction
        hasUnsavedChanges.value = true
        validate()
    }


    private fun validate() {
        isValid.value = name.value.isNotBlank() &&
                selectedGesture.value != null &&
                action.value != SwitchAction(SwitchAction.ACTION_NONE)
    }

    private fun gestureAlreadyAssigned(): Boolean {
        val gestureId = selectedGesture.value?.id ?: return false
        return code == null && store.find(gestureId, profileId) != null
    }

    @StringRes
    fun saveBlockReason(): Int? = when {
        action.value == SwitchAction(SwitchAction.ACTION_NONE) -> R.string.toast_switch_action_required
        gestureAlreadyAssigned() -> R.string.toast_camera_gesture_already_assigned
        else -> null
    }

    fun save(context: Context, completion: ((Boolean) -> Unit)) {
        if (gestureAlreadyAssigned()) {
            completion(false)
            return
        }
        val event = SwitchEvent(
            type = SWITCH_EVENT_TYPE_CAMERA,
            name = name.value.trim(),
            code = selectedGesture.value?.id ?: "",
            pressAction = action.value,
            holdActions = emptyList()
        )

        if (store.find(event.code, profileId) == null) {
            store.add(event, context, profileId) { success ->
                if (success) {
                    completion(true)
                } else {
                    completion(false)
                }
            }
        } else {
            store.update(event, context, profileId) { success ->
                if (success) {
                    completion(true)
                } else {
                    completion(false)
                }
            }
        }
    }

    fun delete(context: Context, completion: (Boolean) -> Unit) {
        val event = store.find(code ?: "", profileId)
        if (event == null) {
            completion(false)
            return
        }
        store.remove(event, context, profileId) { success ->
            if (success) {
                completion(true)
            } else {
                completion(false)
            }
        }
    }
}
