package com.enaboapps.switchify.switches.profiles

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID

internal class SwitchProfileRepository internal constructor(
    private val persistence: SwitchProfilePersistence,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private val mutex = Mutex()
    private var initialized = false
    private val placeholder = SwitchProfileDocument(
        activeProfileId = "uninitialized",
        profiles = listOf(SwitchProfile("uninitialized", "Default", emptyList()))
    )
    private val _document = MutableStateFlow(placeholder)
    val document: StateFlow<SwitchProfileDocument> = _document.asStateFlow()

    suspend fun initialize() = mutex.withLock {
        if (initialized) {
            adoptLateLegacyLocked()
            return@withLock
        }
        initializeLocked()
    }

    suspend fun refresh() = mutex.withLock {
        initializeLocked()
    }

    fun profiles(): List<SwitchProfile> = _document.value.profiles

    fun activeProfile(): SwitchProfile = profile(_document.value.activeProfileId)
        ?: _document.value.profiles.first()

    fun profile(profileId: String): SwitchProfile? =
        _document.value.profiles.firstOrNull { it.id == profileId }

    fun events(profileId: String = _document.value.activeProfileId): List<SwitchEvent> =
        profile(profileId)?.switches.orEmpty().map { it.copy(pressAction = it.pressAction.normalized(), holdActions = it.holdActions.map { action -> action.normalized() }) }

    suspend fun createEmpty(name: String): SwitchProfileMutationResult =
        create(name, emptyList())

    suspend fun duplicate(profileId: String, name: String): SwitchProfileMutationResult {
        val source = profile(profileId) ?: return SwitchProfileMutationResult.NotFound
        return create(name, source.switches)
    }

    suspend fun rename(profileId: String, name: String): SwitchProfileMutationResult = mutex.withLock {
        if (!ensureInitializedLocked()) return@withLock SwitchProfileMutationResult.StorageFailure
        val current = profile(profileId) ?: return@withLock SwitchProfileMutationResult.NotFound
        validateName(name, profileId)?.let { return@withLock it }
        val renamed = current.copy(name = name.trim())
        val updated = _document.value.copy(
            profiles = _document.value.profiles.map { if (it.id == profileId) renamed else it }
        )
        if (!persist(updated)) return@withLock SwitchProfileMutationResult.StorageFailure
        SwitchProfileMutationResult.Success(renamed)
    }

    suspend fun delete(profileId: String): SwitchProfileMutationResult = mutex.withLock {
        if (!ensureInitializedLocked()) return@withLock SwitchProfileMutationResult.StorageFailure
        val current = profile(profileId) ?: return@withLock SwitchProfileMutationResult.NotFound
        if (_document.value.activeProfileId == profileId) {
            return@withLock SwitchProfileMutationResult.ActiveProfile
        }
        if (_document.value.profiles.size == 1) {
            return@withLock SwitchProfileMutationResult.LastProfile
        }
        val updated = _document.value.copy(
            profiles = _document.value.profiles.filterNot { it.id == profileId }
        )
        if (!persist(updated)) return@withLock SwitchProfileMutationResult.StorageFailure
        SwitchProfileMutationResult.Success(current)
    }

    suspend fun replaceEvents(profileId: String, events: List<SwitchEvent>): Boolean = mutex.withLock {
        if (!ensureInitializedLocked()) return@withLock false
        val current = profile(profileId) ?: return@withLock false
        if (events.map { it.code }.distinct().size != events.size) return@withLock false
        val updatedProfile = current.copy(
            switches = events.map { it.copy(pressAction = it.pressAction.normalized(), holdActions = it.holdActions.map { action -> action.normalized() }) }
        )
        persist(
            _document.value.copy(
                profiles = _document.value.profiles.map {
                    if (it.id == profileId) updatedProfile else it
                }
            )
        )
    }

    suspend fun updateEvent(
        profileId: String,
        code: String,
        transform: (SwitchEvent) -> SwitchEvent
    ): Boolean = mutex.withLock {
        if (!ensureInitializedLocked()) return@withLock false
        val current = profile(profileId) ?: return@withLock false
        val event = current.switches.firstOrNull { it.code == code } ?: return@withLock false
        val updatedEvent = transform(event)
        if (updatedEvent.code != code) return@withLock false
        val updatedProfile = current.copy(
            switches = current.switches.map { if (it.code == code) updatedEvent else it }
        )
        persist(_document.value.copy(
            profiles = _document.value.profiles.map { if (it.id == profileId) updatedProfile else it }
        ))
    }

    suspend fun commitActiveProfile(profileId: String): Boolean = mutex.withLock {
        if (!ensureInitializedLocked()) return@withLock false
        if (profile(profileId) == null) return@withLock false
        persist(_document.value.copy(activeProfileId = profileId))
    }

    suspend fun commitActiveProfile(
        profileId: String,
        prepare: (SwitchProfile, SwitchProfile) -> Boolean,
        rollback: (SwitchProfile) -> Unit
    ): SwitchProfile? = mutex.withLock {
        if (!ensureInitializedLocked()) return@withLock null
        val target = profile(profileId) ?: return@withLock null
        val previous = profile(_document.value.activeProfileId) ?: return@withLock null
        if (!prepare(target, previous)) return@withLock null
        if (!persist(_document.value.copy(activeProfileId = profileId))) {
            rollback(previous)
            return@withLock null
        }
        target
    }

    private suspend fun create(
        name: String,
        switches: List<SwitchEvent>
    ): SwitchProfileMutationResult = mutex.withLock {
        if (!ensureInitializedLocked()) return@withLock SwitchProfileMutationResult.StorageFailure
        validateName(name)?.let { return@withLock it }
        val created = SwitchProfile(
            id = idFactory(),
            name = name.trim(),
            switches = switches.map { it.copy(pressAction = it.pressAction.normalized(), holdActions = it.holdActions.map { action -> action.normalized() }) }
        )
        val updated = _document.value.copy(profiles = _document.value.profiles + created)
        if (!persist(updated)) return@withLock SwitchProfileMutationResult.StorageFailure
        SwitchProfileMutationResult.Success(created)
    }

    private fun validateName(
        name: String,
        excludedProfileId: String? = null
    ): SwitchProfileMutationResult.InvalidName? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SwitchProfileMutationResult.InvalidName("required")
        val exists = _document.value.profiles.any {
            it.id != excludedProfileId && it.name.equals(trimmed, ignoreCase = true)
        }
        return if (exists) SwitchProfileMutationResult.InvalidName("duplicate") else null
    }

    private suspend fun ensureInitializedLocked(): Boolean {
        if (initialized) return true
        return initializeLocked()
    }

    private suspend fun initializeLocked(): Boolean {
        val storedResult = persistence.readProfiles()
        val stored = when {
            storedResult.isSuccess -> storedResult.getOrNull()
            storedResult.exceptionOrNull() is CorruptSwitchDataException -> {
                if (persistence.backUpCorruptProfiles().isFailure) return failed("backup corrupt profiles")
                null
            }
            else -> return failed("read profiles", storedResult.exceptionOrNull())
        }
        if (stored != null) {
            if (isValidDocument(stored)) {
                val normalized = normalize(stored)
                if (normalized != stored && persistence.writeProfiles(normalized).isFailure) {
                    return failed("write normalized profiles")
                }
                _document.value = normalized
                initialized = true
                adoptLateLegacyLocked()
                return true
            }
            if (stored.version > SwitchProfileDocument.CURRENT_VERSION) return failed("newer schema version")
            if (persistence.backUpCorruptProfiles().isFailure) return failed("backup invalid profiles")
        }
        val legacyResult = persistence.readLegacyEvents()
        val legacyEvents = when {
            legacyResult.isSuccess -> legacyResult.getOrNull()
            legacyResult.exceptionOrNull() is CorruptSwitchDataException -> {
                if (persistence.backUpCorruptLegacyEvents().isFailure) return failed("backup corrupt legacy events")
                null
            }
            else -> return failed("read legacy events", legacyResult.exceptionOrNull())
        }
        val resolved = newDocument(legacyEvents.orEmpty())
        val writeResult = persistence.writeProfiles(resolved)
        if (writeResult.isFailure) return failed("write migrated profiles", writeResult.exceptionOrNull())
        _document.value = resolved
        initialized = true
        if (legacyEvents != null) persistence.deleteLegacyEvents()
        return true
    }

    private fun failed(stage: String, error: Throwable? = null): Boolean {
        runCatching { Log.w(TAG, "Switch profile initialization failed at: $stage", error) }
        return false
    }

    private suspend fun adoptLateLegacyLocked() {
        val current = _document.value
        if (!isEmptyDefaultDocument(current)) return
        val legacyResult = persistence.readLegacyEvents()
        if (legacyResult.isFailure) return
        val legacyEvents = legacyResult.getOrNull() ?: return
        val migratedProfile = current.profiles.single().copy(
            switches = legacyEvents.map { it.copy(pressAction = it.pressAction.normalized(), holdActions = it.holdActions.map { action -> action.normalized() }) }
        )
        val migrated = current.copy(profiles = listOf(migratedProfile))
        if (persistence.writeProfiles(migrated).isFailure) return
        _document.value = migrated
        persistence.deleteLegacyEvents()
    }

    private fun isEmptyDefaultDocument(document: SwitchProfileDocument): Boolean {
        val profile = document.profiles.singleOrNull() ?: return false
        return profile.id == document.activeProfileId &&
            profile.name == "Default" &&
            profile.switches.isEmpty()
    }

    private suspend fun persist(document: SwitchProfileDocument): Boolean {
        val normalized = normalize(document)
        if (persistence.writeProfiles(normalized).isFailure) return false
        _document.value = normalized
        return true
    }

    private fun normalize(document: SwitchProfileDocument): SwitchProfileDocument = document.copy(
        profiles = document.profiles.map { profile ->
            profile.copy(switches = profile.switches.map { event ->
                event.copy(pressAction = event.pressAction.normalized(), holdActions = event.holdActions.map { it.normalized() })
            })
        }
    )

    private fun isValidDocument(document: SwitchProfileDocument): Boolean = runCatching {
        hasValidStructure(document)
    }.getOrDefault(false)

    private fun hasValidStructure(document: SwitchProfileDocument): Boolean {
        if (document.version != SwitchProfileDocument.CURRENT_VERSION) return false
        if (document.profiles.isEmpty()) return false
        if (document.profiles.none { it.id == document.activeProfileId }) return false
        if (document.profiles.any { it.id.isBlank() || it.name.isBlank() }) return false
        if (document.profiles.map { it.id }.distinct().size != document.profiles.size) return false
        return document.profiles.map { it.name.lowercase(Locale.ROOT) }.distinct().size ==
            document.profiles.size
    }

    private fun newDocument(events: List<SwitchEvent>): SwitchProfileDocument {
        val defaultProfile = SwitchProfile(
            id = idFactory(),
            name = "Default",
            switches = events.map { it.copy(pressAction = it.pressAction.normalized(), holdActions = it.holdActions.map { action -> action.normalized() }) }
        )
        return SwitchProfileDocument(
            activeProfileId = defaultProfile.id,
            profiles = listOf(defaultProfile)
        )
    }

    companion object {
        private const val TAG = "SwitchProfileRepository"

        @Volatile
        private var instance: SwitchProfileRepository? = null

        fun getInstance(context: Context): SwitchProfileRepository {
            return instance ?: synchronized(this) {
                instance ?: SwitchProfileRepository(
                    SwitchProfileLocalPersistence(context)
                ).also { instance = it }
            }
        }
    }
}
