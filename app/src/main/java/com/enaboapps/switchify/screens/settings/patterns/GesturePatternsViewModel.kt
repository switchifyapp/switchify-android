package com.enaboapps.switchify.screens.settings.patterns

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.service.gestures.patterns.model.GesturePattern
import com.enaboapps.switchify.service.gestures.patterns.store.GesturePatternStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GesturePatternsViewModel : ViewModel() {
    private val _patterns = MutableStateFlow<List<GesturePattern>>(emptyList())
    val patterns: StateFlow<List<GesturePattern>> = _patterns.asStateFlow()

    private val _selectedPattern = MutableStateFlow<GesturePattern?>(null)
    val selectedPattern: StateFlow<GesturePattern?> = _selectedPattern.asStateFlow()

    private val _isEditDialogVisible = MutableStateFlow(false)
    val isEditDialogVisible: StateFlow<Boolean> = _isEditDialogVisible.asStateFlow()

    private val _newPatternName = MutableStateFlow("")
    val newPatternName: StateFlow<String> = _newPatternName.asStateFlow()

    private var patternStore: GesturePatternStore? = null

    fun initialize(context: Context) {
        patternStore = GesturePatternStore(context)
        loadPatterns()
    }

    private fun loadPatterns() {
        viewModelScope.launch {
            patternStore?.let {
                _patterns.value = it.getPatterns()
            }
        }
    }

    fun showEditDialog(pattern: GesturePattern) {
        _selectedPattern.value = pattern
        _newPatternName.value = pattern.name
        _isEditDialogVisible.value = true
    }

    fun hideEditDialog() {
        _isEditDialogVisible.value = false
        _selectedPattern.value = null
        _newPatternName.value = ""
    }

    fun updatePatternName(newName: String) {
        _newPatternName.value = newName
    }

    fun savePatternName() {
        viewModelScope.launch {
            val pattern = _selectedPattern.value ?: return@launch
            val newName = _newPatternName.value

            if (newName.isNotBlank() && newName != pattern.name) {
                patternStore?.updatePatternName(pattern.id, newName)
                loadPatterns() // Reload patterns after update
            }

            hideEditDialog()
        }
    }

    fun deletePattern(pattern: GesturePattern) {
        viewModelScope.launch {
            patternStore?.removePattern(pattern.id)
            loadPatterns() // Reload patterns after deletion
        }
    }
} 