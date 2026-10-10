package com.enaboapps.switchify.pc.control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PcControlSetupPhase {
    Welcome,
    OpeningSurface,
    Bluetooth,
    Complete;

    val stepNumber: Int get() = ordinal + 1

    companion object {
        val STEP_COUNT = entries.size - 1
    }
}

class PcControlSetup(private val storage: PcPreferenceStorage) {
    private val _phase = MutableStateFlow(
        if (storage.getString(KEY) == COMPLETE_VALUE) PcControlSetupPhase.Complete else PcControlSetupPhase.Welcome
    )
    val phase: StateFlow<PcControlSetupPhase> = _phase.asStateFlow()

    val isComplete: Boolean get() = _phase.value == PcControlSetupPhase.Complete

    fun next() {
        _phase.value = when (_phase.value) {
            PcControlSetupPhase.Welcome -> PcControlSetupPhase.OpeningSurface
            PcControlSetupPhase.OpeningSurface -> PcControlSetupPhase.Bluetooth
            else -> return
        }
    }

    fun back(): Boolean {
        _phase.value = when (_phase.value) {
            PcControlSetupPhase.OpeningSurface -> PcControlSetupPhase.Welcome
            PcControlSetupPhase.Bluetooth -> PcControlSetupPhase.OpeningSurface
            else -> return false
        }
        return true
    }

    fun complete() {
        if (isComplete) return
        storage.putString(KEY, COMPLETE_VALUE)
        _phase.value = PcControlSetupPhase.Complete
    }

    companion object {
        const val KEY = "firstRunSetup.v1"
        private const val COMPLETE_VALUE = "complete"
    }
}
