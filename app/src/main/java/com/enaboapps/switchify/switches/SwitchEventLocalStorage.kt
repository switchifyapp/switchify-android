package com.enaboapps.switchify.switches

import android.content.Context
import com.enaboapps.switchify.switches.profiles.SwitchProfileRepository

class SwitchEventLocalStorage {
    suspend fun loadFromFile(context: Context): Set<SwitchEvent> {
        val repository = SwitchProfileRepository.getInstance(context)
        repository.initialize()
        return repository.events().toSet()
    }

    suspend fun saveToFile(context: Context, switchEvents: Set<SwitchEvent>): Boolean {
        val repository = SwitchProfileRepository.getInstance(context)
        repository.initialize()
        return repository.replaceEvents(
            repository.document.value.activeProfileId,
            switchEvents.toList()
        )
    }
}
