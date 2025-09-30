package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SupportedActionsPolicy
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore

class AddEditCameraSwitchScreenModel : ViewModel() {

    companion object {
        private const val TAG = "AddEditCameraSwitchScreenModel"
    }

    var name = ""
    val selectedGesture = mutableStateOf<CameraSwitchFacialGesture?>(
        CameraSwitchFacialGesture(CameraSwitchFacialGesture.SMILE)
    )
    val action = mutableStateOf(SwitchAction(SwitchAction.ACTION_SELECT))
    val isValid = mutableStateOf(false)
    val showDeleteConfirmation = mutableStateOf(false)

    private lateinit var store: SwitchEventStore
    private var code: String? = null

    fun init(code: String?, context: Context) {
        store = SwitchEventStore.getInstance()
        this.code = code

        if (code != null) {
            val event = store.find(code)
            event?.let {
                name = it.name
                selectedGesture.value = CameraSwitchFacialGesture(it.code)
                val allowed = SupportedActionsPolicy.supportedActionIds(context)
                action.value =
                    if (allowed.contains(it.pressAction.id)) it.pressAction else SwitchAction(
                        SwitchAction.ACTION_SELECT
                    )
            }
        } else {
            name = ""
        }
        validate()
    }

    fun updateName(newName: String) {
        name = newName
        validate()
    }

    fun setGesture(gesture: CameraSwitchFacialGesture) {
        selectedGesture.value = gesture
        validate()

    }

    fun setAction(newAction: SwitchAction) {
        action.value = newAction
        validate()
    }


    private fun validate() {
        isValid.value = name.isNotBlank() &&
                selectedGesture.value != null &&
                action.value != SwitchAction(SwitchAction.ACTION_NONE)
    }

    fun save(context: Context, completion: ((Boolean) -> Unit)) {
        val event = SwitchEvent(
            type = SWITCH_EVENT_TYPE_CAMERA,
            name = name.trim(),
            code = selectedGesture.value?.id ?: "",
            pressAction = action.value,
            holdActions = emptyList()
        )

        if (store.find(event.code) == null) {
            store.add(event, context) { success ->
                if (success) {
                    completion(true)
                } else {
                    completion(false)
                }
            }
        } else {
            store.update(event, context) { success ->
                if (success) {
                    completion(true)
                } else {
                    completion(false)
                }
            }
        }
    }

    fun delete(context: Context, completion: (Boolean) -> Unit) {
        val event = store.find(code ?: "")
        event?.let {
            store.remove(it, context) { success ->
                if (success) {
                    completion(true)
                } else {
                    completion(false)
                }
            }
        }
    }


    private fun getGestureDisplayName(gestureId: String): String {
        return when (gestureId) {
            CameraSwitchFacialGesture.SMILE -> "Smile"
            CameraSwitchFacialGesture.LEFT_WINK -> "Left Wink"
            CameraSwitchFacialGesture.RIGHT_WINK -> "Right Wink"
            CameraSwitchFacialGesture.BLINK -> "Blink"
            CameraSwitchFacialGesture.HEAD_TURN_LEFT -> "Head Turn Left"
            CameraSwitchFacialGesture.HEAD_TURN_RIGHT -> "Head Turn Right"
            CameraSwitchFacialGesture.HEAD_TURN_UP -> "Head Turn Up"
            CameraSwitchFacialGesture.HEAD_TURN_DOWN -> "Head Turn Down"
            else -> "Face Gesture"
        }
    }

} 
