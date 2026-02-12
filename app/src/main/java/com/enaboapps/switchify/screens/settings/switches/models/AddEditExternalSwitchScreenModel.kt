package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SupportedActionsPolicy
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchAction.Companion.ACTION_MOVE_TO_NEXT_ITEM
import com.enaboapps.switchify.switches.SwitchAction.Companion.ACTION_MOVE_TO_PREVIOUS_ITEM
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import kotlinx.coroutines.launch

class AddEditExternalSwitchScreenModel : ViewModel() {

    companion object {
        private const val TAG = "AddEditExternalSwitchScreenModel"
    }

    private val store = SwitchEventStore.getInstance()
    private var code: String? = null
    private var isInitialized = false

    var name = ""

    val switchCaptured = MutableLiveData(false)
    val shouldSave = MutableLiveData(false)
    val isValid = MutableLiveData(false)
    val allowLongPress = MutableLiveData(true)
    val refreshingLongPressActions = MutableLiveData(false)

    // Actions for press and long press
    val pressAction = MutableLiveData<SwitchAction>().apply {
        value = SwitchAction(SwitchAction.ACTION_SELECT)
    }
    val longPressActions = MutableLiveData<List<SwitchAction>>(emptyList())

    fun init(code: String?, context: Context) {
        this.code = code

        if (code != null) {
            reload(context)
        } else {
            name = ""
            pressAction.value = SwitchAction(SwitchAction.ACTION_SELECT)
            longPressActions.value = emptyList()
            updateAllowLongPress(context)
            isInitialized = true
            validateIfInitialized()
        }
    }

    private fun reload(context: Context) {
        viewModelScope.launch {
            if (code != null) {
                val event = store.find(code ?: "")
                name = event?.name ?: ""
                val initialPress = event?.pressAction ?: SwitchAction(SwitchAction.ACTION_SELECT)
                val allowed = SupportedActionsPolicy.supportedActionIds(context)
                pressAction.value =
                    if (allowed.contains(initialPress.id)) initialPress else SwitchAction(
                        SwitchAction.ACTION_SELECT
                    )
                longPressActions.value = (event?.holdActions ?: emptyList()).map { a ->
                    if (allowed.contains(a.id)) a else SwitchAction(SwitchAction.ACTION_SELECT)
                }
                updateAllowLongPress(context)
                shouldSave.value = true
                switchCaptured.value = true
            } else {
                name = ""
                pressAction.value = SwitchAction(SwitchAction.ACTION_SELECT)
                longPressActions.value = emptyList()
                updateAllowLongPress(context)
            }
            isInitialized = true
            validateIfInitialized()
        }
    }

    private fun validateIfInitialized() {
        if (isInitialized) {
            validate()
        }
    }

    fun processKeyCode(key: Key, context: Context) {
        Log.d(TAG, "processKeyCode: ${key.nativeKeyCode}")

        // If switch already exists, don't save and show toast
        if (store.find(key.nativeKeyCode.toString()) != null) {
            shouldSave.value = false
            Toast.makeText(context, "Switch already exists", Toast.LENGTH_SHORT).show()
            return
        }

        code = key.nativeKeyCode.toString()
        validateIfInitialized()
        shouldSave.value = true
        switchCaptured.value = true

    }

    fun addLongPressAction(action: SwitchAction) {
        val currentActions = longPressActions.value?.toMutableList() ?: mutableListOf()
        currentActions.add(action)
        longPressActions.value = currentActions
        validateIfInitialized()
    }

    fun removeLongPressAction(index: Int) {
        val currentActions = longPressActions.value?.toMutableList() ?: mutableListOf()
        currentActions.removeAt(index)
        longPressActions.value = currentActions
        validateIfInitialized()
        refreshLongPressActions()
    }

    fun moveLongPressAction(fromIndex: Int, toIndex: Int) {
        val currentActions = longPressActions.value?.toMutableList() ?: return
        if (fromIndex in currentActions.indices && toIndex in currentActions.indices) {
            val action = currentActions.removeAt(fromIndex)
            currentActions.add(toIndex, action)
            longPressActions.value = currentActions
            validateIfInitialized()
        }
    }

    fun refreshLongPressActions() {
        refreshingLongPressActions.value = true
        Handler(Looper.getMainLooper()).postDelayed({
            refreshingLongPressActions.value = false
        }, 300)
    }

    /**
     * Reloads long press actions from the store.
     * Call this when returning from the LongPressActionsScreen to sync state.
     */
    fun reloadLongPressActionsFromStore(context: Context) {
        if (code != null) {
            val event = store.find(code ?: "")
            val allowed = SupportedActionsPolicy.supportedActionIds(context)
            longPressActions.value = (event?.holdActions ?: emptyList()).map { a ->
                if (allowed.contains(a.id)) a else SwitchAction(SwitchAction.ACTION_SELECT)
            }
        }
    }

    fun updateLongPressAction(oldAction: SwitchAction, newAction: SwitchAction) {
        val currentActions = longPressActions.value?.toMutableList() ?: mutableListOf()
        val index = currentActions.indexOf(oldAction)
        if (index != -1) {
            currentActions[index] = newAction
            longPressActions.value = currentActions
        }
        validateIfInitialized()
    }

    fun setPressAction(action: SwitchAction, context: Context) {
        pressAction.value = action
        updateAllowLongPress(context)
        validateIfInitialized()
    }

    fun updateName(name: String) {
        this.name = name
        validateIfInitialized()
    }

    private fun updateAllowLongPress(context: Context) {
        val settings = ScanSettings(context)
        val next = ACTION_MOVE_TO_NEXT_ITEM
        val previous = ACTION_MOVE_TO_PREVIOUS_ITEM
        var pressAction = pressAction.value
        val isMoveRepeat = settings.isMoveRepeatEnabled()
        val isMoveAction = pressAction?.id == next || pressAction?.id == previous
        allowLongPress.value = !(isMoveRepeat && isMoveAction)
        println("Allow long press: ${allowLongPress.value}, isMoveRepeat: $isMoveRepeat, isMoveAction: $isMoveAction")
    }

    private fun validate() {
        isValid.value = store.validateSwitchEvent(buildSwitchEvent())
    }

    private fun buildSwitchEvent(): SwitchEvent {
        return SwitchEvent(
            type = SWITCH_EVENT_TYPE_EXTERNAL,
            name = name.trim(),
            code = code ?: "",
            pressAction = pressAction.value ?: SwitchAction(SwitchAction.ACTION_SELECT),
            holdActions = longPressActions.value ?: emptyList()
        )
    }

    fun save(context: Context, completion: ((Boolean) -> Unit)) {
        if (shouldSave.value == true) {
            val event = buildSwitchEvent()
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
                shouldSave.value = false
            }
        }
    }

    fun delete(context: Context, completion: (Boolean) -> Unit) {
        val event = store.find(code ?: "")
        event?.let {
            store.remove(it, context) { success ->
                completion(success)
            }
        }
    }


    private fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> "Space"
            KeyEvent.KEYCODE_ENTER -> "Enter"
            KeyEvent.KEYCODE_TAB -> "Tab"
            KeyEvent.KEYCODE_DPAD_UP -> "Up Arrow"
            KeyEvent.KEYCODE_DPAD_DOWN -> "Down Arrow"
            KeyEvent.KEYCODE_DPAD_LEFT -> "Left Arrow"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "Right Arrow"
            KeyEvent.KEYCODE_BACK -> "Back"
            KeyEvent.KEYCODE_ESCAPE -> "Escape"
            KeyEvent.KEYCODE_A -> "A"
            KeyEvent.KEYCODE_B -> "B"
            KeyEvent.KEYCODE_C -> "C"
            KeyEvent.KEYCODE_D -> "D"
            KeyEvent.KEYCODE_E -> "E"
            KeyEvent.KEYCODE_F -> "F"
            KeyEvent.KEYCODE_G -> "G"
            KeyEvent.KEYCODE_H -> "H"
            KeyEvent.KEYCODE_I -> "I"
            KeyEvent.KEYCODE_J -> "J"
            KeyEvent.KEYCODE_K -> "K"
            KeyEvent.KEYCODE_L -> "L"
            KeyEvent.KEYCODE_M -> "M"
            KeyEvent.KEYCODE_N -> "N"
            KeyEvent.KEYCODE_O -> "O"
            KeyEvent.KEYCODE_P -> "P"
            KeyEvent.KEYCODE_Q -> "Q"
            KeyEvent.KEYCODE_R -> "R"
            KeyEvent.KEYCODE_S -> "S"
            KeyEvent.KEYCODE_T -> "T"
            KeyEvent.KEYCODE_U -> "U"
            KeyEvent.KEYCODE_V -> "V"
            KeyEvent.KEYCODE_W -> "W"
            KeyEvent.KEYCODE_X -> "X"
            KeyEvent.KEYCODE_Y -> "Y"
            KeyEvent.KEYCODE_Z -> "Z"
            else -> "Key $keyCode"
        }
    }

}
