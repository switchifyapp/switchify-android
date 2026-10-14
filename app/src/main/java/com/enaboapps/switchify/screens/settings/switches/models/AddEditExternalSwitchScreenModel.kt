package com.enaboapps.switchify.screens.settings.switches.models

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SupportedActionsPolicy
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchAction.Companion.ACTION_MOVE_TO_NEXT_ITEM
import com.enaboapps.switchify.switches.SwitchAction.Companion.ACTION_MOVE_TO_PREVIOUS_ITEM
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.switches.SwitchHoldPolicy
import com.enaboapps.switchify.switches.SwitchNameGenerator
import com.enaboapps.switchify.switches.profiles.SwitchProfileRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AddEditExternalSwitchScreenModel : ViewModel() {

    companion object {
        private const val TAG = "AddEditExternalSwitchScreenModel"
    }

    private val store = SwitchEventStore.getInstance()
    private var code: String? = null
    private var profileId: String? = null
    private var isInitialized = false
    private var editing = false

    private var autoName = ""
    private var customName: String? = null
    val name = mutableStateOf("")

    val switchCaptured = MutableLiveData(false)
    val shouldSave = MutableLiveData(false)
    val isValid = MutableLiveData(false)
    val hasUnsavedChanges = MutableLiveData(false)
    val allowLongPress = MutableLiveData(true)
    val refreshingLongPressActions = MutableLiveData(false)

    // Actions for press and long press
    val pressAction = MutableLiveData<SwitchAction>().apply {
        value = SwitchAction(SwitchAction.ACTION_SELECT)
    }
    val longPressActions = MutableLiveData<List<SwitchAction>>(emptyList())

    fun init(code: String?, context: Context, profileId: String? = null) {
        this.code = code
        this.profileId = profileId
        editing = code != null

        if (code != null) {
            reload(context)
            viewModelScope.launch {
                val repository = SwitchProfileRepository.getInstance(context)
                repository.initialize()
                val targetProfileId = profileId ?: repository.document.value.activeProfileId
                this@AddEditExternalSwitchScreenModel.profileId = targetProfileId
                repository.document.map { document ->
                    document.profiles.firstOrNull { it.id == targetProfileId }
                        ?.switches?.firstOrNull { it.code == code }?.holdActions.orEmpty()
                }.distinctUntilChanged().collect { actions ->
                    longPressActions.value = actions
                    validateIfInitialized()
                }
            }
        } else {
            autoName = ""
            customName = null
            refreshName()
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
                val event = store.find(code ?: "", profileId)
                autoName = code?.toIntOrNull()?.let { SwitchNameGenerator.forKeyCode(it) } ?: ""
                customName = event?.name?.takeIf { it.isNotBlank() }
                refreshName()
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
                autoName = ""
                customName = null
                refreshName()
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
        if (store.find(key.nativeKeyCode.toString(), profileId) != null) {
            shouldSave.value = false
            Toast.makeText(context, R.string.toast_switch_already_exists, Toast.LENGTH_SHORT).show()
            return
        }

        code = key.nativeKeyCode.toString()
        autoName = SwitchNameGenerator.forKeyCode(key.nativeKeyCode)
        refreshName()
        hasUnsavedChanges.value = true
        validateIfInitialized()
        shouldSave.value = true
        switchCaptured.value = true

    }

    fun addLongPressAction(action: SwitchAction) {
        val currentActions = longPressActions.value?.toMutableList() ?: mutableListOf()
        currentActions.add(action)
        longPressActions.value = currentActions
        hasUnsavedChanges.value = true
        validateIfInitialized()
    }

    fun removeLongPressAction(index: Int) {
        val currentActions = longPressActions.value?.toMutableList() ?: mutableListOf()
        currentActions.removeAt(index)
        longPressActions.value = currentActions
        hasUnsavedChanges.value = true
        validateIfInitialized()
        refreshLongPressActions()
    }

    fun moveLongPressAction(fromIndex: Int, toIndex: Int) {
        val currentActions = longPressActions.value?.toMutableList() ?: return
        if (fromIndex in currentActions.indices && toIndex in currentActions.indices) {
            val action = currentActions.removeAt(fromIndex)
            currentActions.add(toIndex, action)
            longPressActions.value = currentActions
            hasUnsavedChanges.value = true
            validateIfInitialized()
        }
    }

    fun refreshLongPressActions() {
        refreshingLongPressActions.value = true
        Handler(Looper.getMainLooper()).postDelayed({
            refreshingLongPressActions.value = false
        }, 300)
    }

    fun updateLongPressAction(oldAction: SwitchAction, newAction: SwitchAction) {
        val currentActions = longPressActions.value?.toMutableList() ?: mutableListOf()
        val index = currentActions.indexOf(oldAction)
        if (index != -1) {
            currentActions[index] = newAction
            longPressActions.value = currentActions
            hasUnsavedChanges.value = true
        }
        validateIfInitialized()
    }

    fun setPressAction(action: SwitchAction, context: Context) {
        pressAction.value = action
        hasUnsavedChanges.value = true
        updateAllowLongPress(context)
        validateIfInitialized()
    }

    fun updateName(newName: String) {
        customName = newName.trim().takeIf { it.isNotBlank() }
        refreshName()
        hasUnsavedChanges.value = true
        validateIfInitialized()
    }

    private fun refreshName() {
        name.value = customName ?: autoName
    }

    private fun updateAllowLongPress(context: Context) {
        val settings = ScanSettings(context)
        val next = ACTION_MOVE_TO_NEXT_ITEM
        val previous = ACTION_MOVE_TO_PREVIOUS_ITEM
        var pressAction = pressAction.value
        val isMoveRepeat = settings.isMoveRepeatEnabled()
        val isMoveAction = pressAction?.id == next || pressAction?.id == previous
        val holdEnabled = SwitchHoldPolicy.isEnabled(PreferenceManager(context))
        allowLongPress.value = holdEnabled && !(isMoveRepeat && isMoveAction)
    }

    private fun validate() {
        isValid.value = store.validateSwitchEvent(buildSwitchEvent())
    }

    private fun buildSwitchEvent(): SwitchEvent {
        return SwitchEvent(
            type = SWITCH_EVENT_TYPE_EXTERNAL,
            name = name.value.trim(),
            code = code ?: "",
            pressAction = pressAction.value ?: SwitchAction(SwitchAction.ACTION_SELECT),
            holdActions = longPressActions.value ?: emptyList()
        )
    }

    fun save(context: Context, completion: ((Boolean) -> Unit)) {
        if (shouldSave.value == true) {
            val event = buildSwitchEvent()
            shouldSave.value = false
            val onComplete: (Boolean) -> Unit = { success ->
                viewModelScope.launch {
                    shouldSave.value = !success
                    if (success) hasUnsavedChanges.value = false
                    completion(success)
                }
            }
            if (editing) store.updateDetails(event, context, profileId, onComplete)
            else store.add(event, context, profileId, onComplete)
        }
    }

    fun delete(context: Context, completion: (Boolean) -> Unit) {
        val event = store.find(code ?: "", profileId)
        if (event == null) {
            completion(false)
            return
        }
        store.remove(event, context, profileId) { success ->
            completion(success)
        }
    }
}
